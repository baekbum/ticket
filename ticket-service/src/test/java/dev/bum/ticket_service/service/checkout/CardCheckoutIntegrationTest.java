package dev.bum.ticket_service.service.checkout;

import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutCommandService;
import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutService;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.*;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.checkout.payment.*;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.notification.CardPaymentNotificationService;
import dev.bum.ticket_service.service.payment.payment.*;
import dev.bum.ticket_service.service.payment.virtualAccount.*;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountIssuanceCheckoutIntegrationTest.PersistenceConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CardCheckoutService.class, CardCheckoutCommandService.class, VirtualAccountPaymentLockService.class,
        CardPaymentNotificationService.class, CardPaymentService.class, PaymentService.class,
        PaymentCompletionService.class, TicketRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardCheckoutIntegrationTest {

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("CARD_CHECKOUT_TEST_POSTGRES_URL");
        if (url != null && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/card_checkout_test")) throw new IllegalArgumentException("격리된 카드 테스트 DB만 사용합니다.");
        properties.add("spring.datasource.url", () -> url == null ? "jdbc:h2:mem:ticksy-card-checkout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : url);
        properties.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> url == null ? "sa" : "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired CardCheckoutService service;
    @Autowired CardCheckoutCommandService commands;
    @Autowired CardPaymentNotificationService notifications;
    @Autowired PaymentJpaRepository payments;
    @Autowired ReservationJpaRepository reservations;
    @Autowired EventJpaRepository events;
    @Autowired SeatJpaRepository seats;
    @Autowired TicketJpaRepository tickets;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PaymentGatewayTransactionClient gateway;
    @MockitoBean VirtualAccountPaymentService bank;
    @MockitoBean PaymentExpirationService expirations;
    @MockitoBean SeatCacheService cache;
    final String pgId = "20261115" + UUID.randomUUID();
    final String number = "PAY-CARD-CHECKOUT";
    Long reservationId;
    final AtomicReference<GatewayPaymentPrepareRequest> original = new AtomicReference<>();

    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            payments.deleteAll(); tickets.deleteAll(); reservations.deleteAll(); seats.deleteAll(); events.deleteAll();
            Event event = events.saveAndFlush(Event.builder().title("공연").artistName("artist").venue("venue")
                    .eventDateTime(LocalDateTime.of(2099,12,1,18,0)).status(EventStatus.ON_SALE)
                    .maxTicketsPerPerson(4).totalSeats(1).availableSeats(0).build());
            Reservation reservation = reservations.saveAndFlush(Reservation.builder().event(event).userId("owner")
                    .orderId("ORDER-1").status(ReservationStatus.PENDING_PAYMENT).reservedAt(LocalDateTime.now()).build());
            reservationId = reservation.getReservationId();
            Seat seat = seats.saveAndFlush(Seat.builder().event(event).zone("A").seatRow(1).seatCol(1)
                    .grade(SeatGrade.VIP).price(10000).status(SeatStatus.LOCKED).build());
            tickets.saveAndFlush(Ticket.builder().event(event).reservation(reservation).userId("owner").seat(seat)
                    .status(TicketStatus.PENDING_PAYMENT).build());
            Payment payment = Payment.builder().reservation(reservation).paymentNo(number).method(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.READY).amount(10000).idempotencyKey("same-checkout-key")
                    .requestedAt(LocalDateTime.now()).expiresAt(LocalDateTime.of(2099,1,1,12,0)).build();
            payment.planGatewayCard(); payments.saveAndFlush(payment);
        });
        when(gateway.prepare(any())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            GatewayPaymentPrepareRequest request = inv.getArgument(0);
            GatewayPaymentPrepareRequest first = original.getAndSet(request);
            if (first != null) assertThat(request).isEqualTo(first);
            assertThat(payments.findByPaymentNo(number).orElseThrow().getGatewayExpiresAt()).isNotNull();
            return response(request);
        });
        when(gateway.cardSession(anyString(), any())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(payments.findByPaymentNo(number).orElseThrow().getPgPaymentId()).isEqualTo(pgId);
            return new GatewayCardSessionResponse(1L, pgId, number, "owner", BigDecimal.valueOf(10000),
                    "opaque-test-session", Instant.now().plusSeconds(300), true);
        });
    }

    GatewayPaymentPrepareResponse response(GatewayPaymentPrepareRequest request) {
        return new GatewayPaymentPrepareResponse(pgId, 1L, number, "owner", PaymentMethod.CREDIT_CARD,
                null, request.amount(), request.expiresAt(), "READY");
    }


    @Test void merchantAuthenticatedPrepareAndSessionCallsUseActualHttpAfterEachLocalCommit() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Merchant")).isEqualTo("1");
            assertThat(exchange.getRequestHeaders().getFirst("credentialKey")).isEqualTo("ticksy_test-card-checkout");
            Object result;
            if (exchange.getRequestURI().getPath().endsWith("/prepare")) {
                GatewayPaymentPrepareRequest request = json.readValue(exchange.getRequestBody(),GatewayPaymentPrepareRequest.class);
                assertThat(payments.findByPaymentNo(number).orElseThrow().getGatewayExpiresAt()).isNotNull();
                result = response(request);
            } else {
                GatewayCardSessionRequest request = json.readValue(exchange.getRequestBody(),GatewayCardSessionRequest.class);
                assertThat(request.buyerReference()).isEqualTo("owner");
                assertThat(request.approvalAllowed()).isTrue();
                assertThat(payments.findByPaymentNo(number).orElseThrow().getPgPaymentId()).isEqualTo(pgId);
                result = new GatewayCardSessionResponse(1L,pgId,number,"owner",BigDecimal.valueOf(10000),
                        "opaque-http-session",Instant.now().plusSeconds(300),true);
            }
            calls.incrementAndGet();
            byte[] body = json.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            PaymentGatewayTransactionClient client = feign.Feign.builder()
                    .contract(new org.springframework.cloud.openfeign.support.SpringMvcContract())
                    .retryer(feign.Retryer.NEVER_RETRY)
                    .requestInterceptor(new dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig()
                            .paymentGatewayMerchantRequestInterceptor(1L,"ticksy_test-card-checkout"))
                    .encoder((value,type,template) -> {
                        try {
                            template.header("Content-Type","application/json");
                            template.body(json.writeValueAsBytes(value),java.nio.charset.StandardCharsets.UTF_8);
                        } catch(java.io.IOException failure) { throw new IllegalStateException(failure); }
                    })
                    .decoder((reply,type) -> json.readValue(reply.body().asInputStream(),json.constructType(type)))
                    .target(PaymentGatewayTransactionClient.class,"http://127.0.0.1:"+server.getAddress().getPort());
            doAnswer(inv -> client.prepare(inv.getArgument(0))).when(gateway).prepare(any());
            doAnswer(inv -> client.cardSession(inv.getArgument(0),inv.getArgument(1))).when(gateway).cardSession(anyString(),any());
            service.prepare("owner", number);
            assertThat(service.session("owner",number).paymentSession()).isEqualTo("opaque-http-session");
            assertThat(calls.get()).isEqualTo(2);
        } finally {server.stop(0);}
    }

    @Test void prepareCommitsTheIdBeforeIssuingASessionAndReplaysWithoutAnotherPrepare() {
        assertThat(service.prepare("owner", number).getPgPaymentId()).isEqualTo(pgId);
        assertThat(service.session("owner", number).paymentSession()).isEqualTo("opaque-test-session");
        verify(gateway, times(1)).prepare(any());
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test void sessionWithoutABoundPgPaymentDoesNotPrepareOrRequestASession() {
        assertThatThrownBy(() -> service.session("owner", number))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 준비를 먼저 완료");
        assertThat(payments.findByPaymentNo(number).orElseThrow().getPgPaymentId()).isNull();
        verifyNoInteractions(gateway);
    }

    @Test void aLostPrepareResponseKeepsTheOriginalRequestAndUsesTheSameKeyOnManualRetry() {
        doAnswer(inv -> { original.set(inv.getArgument(0)); throw new IllegalStateException("lost response"); }).when(gateway).prepare(any());
        assertThatThrownBy(() -> service.prepare("owner", number)).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByPaymentNo(number).orElseThrow().getPgPaymentId()).isNull();
        doAnswer(inv -> { assertThat(inv.getArgument(0, GatewayPaymentPrepareRequest.class)).isEqualTo(original.get()); return response(original.get()); })
                .when(gateway).prepare(any());
        assertThat(service.prepare("owner", number).getPgPaymentId()).isEqualTo(pgId);
        verify(gateway, times(2)).prepare(any());
    }

    @Test void otherUsersCannotPrepareOrObtainThePaymentSession() {
        assertThatThrownBy(() -> service.prepare("other", number)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.session("other", number)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gateway);
    }

    @Test void wrongPrepareMetadataDoesNotBindAnUnrelatedPgPayment() {
        GatewayPaymentPrepareRequest request = commands.findPreparation("owner", number).request();
        GatewayPaymentPrepareResponse wrong = new GatewayPaymentPrepareResponse(pgId,2L,number,"owner",
                PaymentMethod.CREDIT_CARD,null,request.amount(),request.expiresAt(),"READY");
        assertThatThrownBy(() -> commands.prepared("owner", number, wrong)).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByPaymentNo(number).orElseThrow().getPgPaymentId()).isNull();
    }

    @Test void standardNotificationsRequireTheBoundPgIdAndCompleteTheLocalBookingIdempotently() {
        service.prepare("owner", number);
        CardPaymentNotification event = new CardPaymentNotification(1, pgId+":APPROVE:INITIAL",1L,pgId,number,"owner",
                PaymentMethod.CREDIT_CARD,PaymentStatus.PAID,BigDecimal.valueOf(10000),"CARD-1",CardCompany.SHINHAN,
                "4111-****-****-1111",LocalDateTime.now());
        assertThat(notifications.receive(event).outcome()).isEqualTo(CardPaymentNotificationResponse.Outcome.ACCEPTED);
        assertThat(notifications.receive(event).outcome()).isEqualTo(CardPaymentNotificationResponse.Outcome.ACCEPTED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(tickets.findAll().getFirst().getStatus()).isEqualTo(TicketStatus.PAID);
        CardPaymentNotification wrong = new CardPaymentNotification(1,"OTHER:APPROVE:INITIAL",1L,"OTHER",number,"owner",
                PaymentMethod.CREDIT_CARD,PaymentStatus.PAID,BigDecimal.valueOf(10000),"CARD-1",CardCompany.SHINHAN,
                "4111-****-****-1111",LocalDateTime.now());
        assertThatThrownBy(() -> notifications.receive(wrong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void newPgCardsCannotChangeToAnUnrelatedBankTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx ->
                payments.findByPaymentNo(number).orElseThrow().switchToBankTransfer())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gateway);
    }
}
