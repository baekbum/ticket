package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.*;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.exception.reservation.GatewayRefundRequestException;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.refund.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import feign.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "app.payment-refund.account-encryption-key=test-refund-encryption-key"})
@ContextConfiguration(classes = GatewayReservationRefundIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GatewayReservationRefundService.class, GatewayReservationRefundCommands.class, RefundAccountCipher.class,
        PaymentRefundProcessService.class, PaymentRefundProcessCreator.class, PaymentRefundRollbackRecorder.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GatewayReservationRefundIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.ticket_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.ticket_service.jpa")
    static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-13T03:00:00Z"), ZoneId.of("Asia/Seoul")); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("REFUND_TEST_POSTGRES_URL");
        if (url != null && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/gateway_refund_test")) {
            throw new IllegalArgumentException("격리된 gateway_refund_test DB만 사용할 수 있습니다.");
        }
        registry.add("spring.datasource.url", () -> url == null ? "jdbc:h2:mem:ticket-gateway-refunds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : url);
        registry.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> url == null ? "sa" : "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired GatewayReservationRefundService service;
    @Autowired GatewayReservationRefundCommands commands;
    @Autowired ReservationJpaRepository reservations;
    @Autowired PaymentJpaRepository payments;
    @Autowired TicketJpaRepository tickets;
    @Autowired SeatJpaRepository seats;
    @Autowired EventJpaRepository events;
    @Autowired PaymentRefundProcessJpaRepository processes;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean PaymentRefundHistoryJpaRepository histories;
    @MockitoBean PaymentGatewayBankRefundClient gateway;
    @MockitoBean PaymentGatewayRefundClient legacyGateway;
    @MockitoBean UserCouponRepository coupons;
    @MockitoBean SeatCacheService cache;
    @MockitoBean TicketPurchaseCountService counts;
    Long reservationId;
    Long eventId;
    List<Long> ticketIds;

    @BeforeEach void setup() {
        reset(gateway, histories, counts);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            histories.deleteAll(); processes.deleteAll(); payments.deleteAll(); tickets.deleteAll();
            reservations.deleteAll(); seats.deleteAll(); events.deleteAll();
            Event event = events.saveAndFlush(Event.builder().title("공연").artistName("artist").venue("venue")
                    .eventDateTime(LocalDateTime.of(2026,12,20,18,0)).cancelDeadlineAt(LocalDateTime.of(2026,12,19,18,0))
                    .saleStartAt(LocalDateTime.of(2026,1,1,0,0)).saleEndAt(LocalDateTime.of(2026,12,1,0,0))
                    .status(EventStatus.ON_SALE).maxTicketsPerPerson(4).totalSeats(4).availableSeats(0).build());
            eventId = event.getEventId();
            Reservation reservation = reservations.saveAndFlush(Reservation.builder().event(event).userId("owner")
                    .orderId("ORDER-" + UUID.randomUUID()).status(ReservationStatus.PAID)
                    .reservedAt(LocalDateTime.of(2026,11,13,10,0)).build());
            reservationId = reservation.getReservationId();
            ticketIds = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                Seat seat = seats.saveAndFlush(Seat.builder().event(event).zone("A").seatRow(1).seatCol(i)
                        .grade(SeatGrade.VIP).price(10000).status(SeatStatus.RESERVED).build());
                Ticket ticket = tickets.saveAndFlush(Ticket.builder().event(event).reservation(reservation)
                        .userId("owner").seat(seat).status(TicketStatus.PAID).build());
                ticketIds.add(ticket.getTicketId());
            }
            Payment payment = Payment.builder().reservation(reservation).paymentNo("PAY-" + UUID.randomUUID())
                    .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.PAID).amount(40000)
                    .idempotencyKey(UUID.randomUUID().toString()).requestedAt(LocalDateTime.of(2026,11,13,10,0)).build();
            payment.planGatewayAccount(BankCompany.KB, LocalDateTime.of(2026,11,14,12,0));
            payment.bindGatewayPayment("20261113" + UUID.randomUUID());
            payments.saveAndFlush(payment);
        });
        when(gateway.refund(anyString(), anyString(), any())).thenAnswer(inv -> result(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), "SUCCEEDED"));
        when(gateway.withdraw(anyString(), anyString(), any())).thenAnswer(inv -> result(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), "WITHDRAWN"));
    }

    BankRefundResult result(String paymentId, String key, GatewayBankRefundRequest request, String outcome) {
        if (request == null) return null;
        BankRefundCommand command = request.toBank(paymentId, key);
        return new BankRefundResult(paymentId, key, command.amount(), outcome, LocalDateTime.of(2026,11,13,12,0), command.fingerprint());
    }

    CancelReservationRequest request(List<Long> ids) {
        return CancelReservationRequest.builder().eventId(eventId).selectedTicketIdList(ids)
                .refundAccount(new RefundAccountRequest(BankCompany.KB,"1234567890","환불자")).build();
    }

    FeignException unavailable() {
        return FeignException.errorStatus("refund", Response.builder().status(503).reason("unavailable")
                .request(Request.create(Request.HttpMethod.POST, "http://pg", Map.of(), new byte[0], StandardCharsets.UTF_8, new RequestTemplate())).build());
    }

    @Test void partialRefundAndCompletedReplayUseOneProcessEvenAfterTicketsAreCancelled() {
        CancelReservationRequest request = request(ticketIds.subList(1,3));
        GatewayRefundActionResponse first = service.request("owner",reservationId,request);
        request.setSelectedTicketIdList(List.of(ticketIds.get(2),ticketIds.get(1)));
        GatewayRefundActionResponse replay = service.request("owner",reservationId,request);
        assertThat(replay.application().getPaymentRefundProcessId()).isEqualTo(first.application().getPaymentRefundProcessId());
        assertThat(replay.outcome()).isEqualTo("SUCCEEDED");
        assertThat(processes.count()).isEqualTo(1);
        verify(gateway,times(1)).refund(anyString(),anyString(),any());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
            assertThat(tickets.findById(ticketIds.get(1)).orElseThrow().getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(tickets.findById(ticketIds.get(0)).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
        });
    }

    @Test void aFullRefundCancelsAllTicketsAndReplaysWithoutAnotherPgCall() {
        GatewayRefundActionResponse first=service.request("owner",reservationId,request(ticketIds));
        assertThat(first.outcome()).isEqualTo("SUCCEEDED");
        assertThat(first.application().getCancellationFeeAmount()).isNotNull();
        assertThat(service.retry("owner",reservationId,first.application().getPaymentRefundProcessId()).outcome()).isEqualTo("SUCCEEDED");
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(payments.findAll().getFirst().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway,times(1)).refund(anyString(),anyString(),any());
    }

    @Test void aLostPgResponseDoesNotRetryInTicksyAndDifferentTargetsOrAccountsAreRejected() {
        doThrow(unavailable()).when(gateway).refund(anyString(),anyString(),any());
        assertThatThrownBy(()->service.request("owner",reservationId,request(ticketIds.subList(1,3)))).isInstanceOf(FeignException.class);
        long id=processes.findAll().getFirst().getPaymentRefundProcessId();
        assertThatThrownBy(()->service.request("owner",reservationId,request(ticketIds.subList(0,2))))
                .isInstanceOf(GatewayRefundRequestException.class);
        CancelReservationRequest changed=request(ticketIds.subList(1,3));
        changed.getRefundAccount().setAccountNumber("other");
        assertThatThrownBy(()->service.request("owner",reservationId,changed)).isInstanceOf(GatewayRefundRequestException.class);
        verify(gateway,times(1)).refund(anyString(),eq(Long.toString(id)),any());
        assertThat(processes.count()).isEqualTo(1);
        doAnswer(inv -> result(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2),"SUCCEEDED")).when(gateway).refund(anyString(),anyString(),any());
        assertThat(service.retry("owner",reservationId,id).outcome()).isEqualTo("SUCCEEDED");
    }

    @Test void withdrawalReleasesTheApplicationButLeavesTheBookingAndAllowsADifferentNewApplication() {
        long id=commands.prepare("owner",reservationId,request(ticketIds.subList(1,3)));
        assertThat(service.withdraw("owner",reservationId,id).outcome()).isEqualTo("WITHDRAWN");
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        GatewayRefundActionResponse next=service.request("owner",reservationId,request(ticketIds.subList(0,2)));
        assertThat(next.application().getPaymentRefundProcessId()).isNotEqualTo(id);
        assertThat(processes.count()).isEqualTo(2);
    }

    @Test void withdrawalOfAnAlreadyRefundedPgRequestRepairsTheLocalBookingAndReturnsSuccess() {
        when(gateway.refund(anyString(),anyString(),any())).thenThrow(unavailable());
        assertThatThrownBy(()->service.request("owner",reservationId,request(ticketIds.subList(1,3)))).isInstanceOf(FeignException.class);
        long id=processes.findAll().getFirst().getPaymentRefundProcessId();
        doAnswer(inv -> result(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2),"SUCCEEDED")).when(gateway).withdraw(anyString(),anyString(),any());
        GatewayRefundActionResponse response=service.withdraw("owner",reservationId,id);
        assertThat(response.outcome()).isEqualTo("SUCCEEDED");
        assertThat(response.message()).contains("이미 환불");
        assertThat(response.application().getStatus()).isEqualTo("LOCAL_SUCCEEDED");
    }

    @Test void anUnconfirmedWithdrawalKeepsItsIntentAndCannotBeTurnedIntoANewRefund() {
        long id=commands.prepare("owner",reservationId,request(ticketIds.subList(1,3)));
        doThrow(unavailable()).when(gateway).withdraw(anyString(),anyString(),any());
        assertThatThrownBy(()->service.withdraw("owner",reservationId,id)).isInstanceOf(FeignException.class);
        assertThat(processes.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.WITHDRAW_REQUESTED);
        assertThatThrownBy(()->service.retry("owner",reservationId,id)).isInstanceOf(GatewayRefundRequestException.class);
        assertThatThrownBy(()->service.request("owner",reservationId,request(ticketIds.subList(0,2)))).isInstanceOf(GatewayRefundRequestException.class);
        verify(gateway,never()).refund(anyString(),anyString(),any());
    }

    @Test void unauthorizedOwnersCannotReadRetryOrWithdrawAnApplication() {
        long id=commands.prepare("owner",reservationId,request(ticketIds.subList(1,3)));
        assertThatThrownBy(()->service.find("another",reservationId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.retry("another",reservationId,id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.withdraw("another",reservationId,id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(gateway);
    }

    @Test void snapshotsKeepCiphertextAndLocalFailureRollsBackSeatsButNotTheOriginalApplication() {
        doAnswer(inv -> { PaymentRefundHistory history=inv.getArgument(0); if(history.getEventType()==PaymentRefundHistoryType.LOCAL_APPLIED)throw new DataIntegrityViolationException("simulated local storage failure");return inv.callRealMethod(); })
                .when(histories).save(any(PaymentRefundHistory.class));
        assertThatThrownBy(()->service.request("owner",reservationId,request(ticketIds.subList(1,3)))).isInstanceOf(RuntimeException.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            PaymentRefundSnapshot snapshot=processes.findAll().getFirst().getRefund().getSnapshot();
            assertThat(snapshot.getRefundAccountCiphertext()).doesNotContain("1234567890").startsWith("v1:");
            assertThat(tickets.findById(ticketIds.get(1)).orElseThrow().getSeat().getStatus()).isEqualTo(SeatStatus.RESERVED);
        });
        assertThat(processes.count()).isEqualTo(1);
        reset(histories);
        long id=processes.findAll().getFirst().getPaymentRefundProcessId();
        assertThat(service.retry("owner",reservationId,id).outcome()).isEqualTo("SUCCEEDED");
    }

    @Test
    @EnabledIfEnvironmentVariable(named="REFUND_TEST_POSTGRES_URL",matches="jdbc:postgresql:.*")
    void nowaitRejectsConcurrentWithdrawalWhileTheRefundTransactionWaitsForPg() throws Exception {
        long id=commands.prepare("owner",reservationId,request(ticketIds.subList(1,3)));
        CountDownLatch entered=new CountDownLatch(1);CountDownLatch release=new CountDownLatch(1);
        when(gateway.refund(anyString(),anyString(),any())).thenAnswer(inv->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return result(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2),"SUCCEEDED");});
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<GatewayRefundActionResponse> first=pool.submit(()->service.retry("owner",reservationId,id));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            long start=System.nanoTime();
            assertThatThrownBy(()->service.withdraw("owner",reservationId,id)).isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1500);
            release.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS).outcome()).isEqualTo("SUCCEEDED");
        } finally {release.countDown();pool.shutdownNow();}
    }
}
