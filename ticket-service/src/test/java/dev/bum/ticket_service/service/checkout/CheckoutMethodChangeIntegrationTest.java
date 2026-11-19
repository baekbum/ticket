package dev.bum.ticket_service.service.checkout;

import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutCommandService;
import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.translate.BankToCardCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.translate.CardToBankCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.translate.CheckoutMethodChangeCommandService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.CheckoutVirtualAccountService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutCommandService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutService;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.checkout.dto.*;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.*;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.checkout.*;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.checkout.payment.*;
import dev.bum.ticket_service.service.event.event.EventBookingGuard;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.notification.CardPaymentNotificationService;
import dev.bum.ticket_service.service.payment.payment.*;
import dev.bum.ticket_service.service.payment.virtualAccount.*;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import feign.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountIssuanceCheckoutIntegrationTest.PersistenceConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CheckoutService.class, CheckoutTransactionService.class, CheckoutPaymentService.class, CheckoutVirtualAccountService.class,
        BankToCardCheckoutService.class, CardToBankCheckoutService.class, CheckoutMethodChangeCommandService.class,
        CardCheckoutService.class, CardCheckoutCommandService.class,
        VirtualAccountIssuanceCheckoutService.class, VirtualAccountIssuanceCheckoutCommandService.class,
        VirtualAccountPaymentLockService.class, CardPaymentNotificationService.class, CardPaymentService.class,
        PaymentService.class, PaymentCompletionService.class, TicketRepositoryImpl.class,
        CheckoutMethodChangeIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheckoutMethodChangeIntegrationTest {

    @Configuration static class Config { @Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); } }

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("TICKET_METHOD_TEST_POSTGRES_URL");
        if(url!=null && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/ticket_method_test"))throw new IllegalArgumentException("격리된 전환 테스트 DB만 사용합니다.");
        properties.add("spring.datasource.url",()->url==null?"jdbc:h2:mem:ticket-card-method;MODE=PostgreSQL;DB_CLOSE_DELAY=-1":url);
        properties.add("spring.datasource.driver-class-name",()->url==null?"org.h2.Driver":"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->url==null?"sa":"postgres");
        properties.add("spring.datasource.password",()->"");
        properties.add("spring.jpa.hibernate.ddl-auto",()->"create-drop");
        properties.add("spring.sql.init.mode",()->"never");
    }

    @Autowired CheckoutService checkout;
    @Autowired CheckoutTransactionService commands;
    @Autowired CardToBankCheckoutService switches;
    @Autowired CardPaymentNotificationService notifications;
    @Autowired CheckoutMethodChangeCommandService results;
    @Autowired CheckoutAttemptJpaRepository attempts;
    @Autowired PaymentJpaRepository payments;
    @Autowired ReservationJpaRepository reservations;
    @Autowired EventJpaRepository events;
    @Autowired SeatJpaRepository seats;
    @Autowired TicketJpaRepository tickets;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayTransactionClient gateway;
    @MockitoBean ReservationRepository reservationRepository;
    @MockitoBean QueueAccessService queue;
    @MockitoBean EventBookingGuard guard;
    @MockitoBean CheckoutIdempotencyKeyGenerator keys;
    @MockitoBean VirtualAccountCancelOutboxService cancellations;
    @MockitoBean VirtualAccountPaymentService bank;
    @MockitoBean PaymentExpirationService expirations;
    @MockitoBean SeatCacheService cache;
    String pgId;
    String paymentNo;
    Long eventId;
    Long seatId;
    Long reservationId;
    LocalDateTime deadline;
    final AtomicReference<GatewayCardMethodChangeRequest> original=new AtomicReference<>();

    @BeforeEach void setup() {
        reset(gateway);
        original.set(null); pgId="20261116"+UUID.randomUUID(); paymentNo="PAY-SWITCH";
        deadline=LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.MICROS);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            attempts.deleteAll(); payments.deleteAll(); tickets.deleteAll(); reservations.deleteAll(); seats.deleteAll(); events.deleteAll();
            Event event=events.saveAndFlush(Event.builder().title("공연").artistName("artist").venue("venue")
                    .eventDateTime(LocalDateTime.now().plusMonths(2)).status(EventStatus.ON_SALE).maxTicketsPerPerson(4).totalSeats(1).availableSeats(0).build());
            eventId=event.getEventId();
            Reservation reservation=reservations.saveAndFlush(Reservation.builder().event(event).userId("owner").orderId("ORDER-1")
                    .status(ReservationStatus.PENDING_PAYMENT).reservedAt(LocalDateTime.now()).build());
            reservationId=reservation.getReservationId();
            Seat seat=seats.saveAndFlush(Seat.builder().event(event).zone("A").seatRow(1).seatCol(1).grade(SeatGrade.VIP).price(10000).status(SeatStatus.LOCKED).build());
            seatId=seat.getSeatId();
            tickets.saveAndFlush(Ticket.builder().event(event).reservation(reservation).userId("owner").seat(seat).status(TicketStatus.PENDING_PAYMENT).build());
            Payment payment=Payment.builder().reservation(reservation).paymentNo(paymentNo).method(PaymentMethod.CREDIT_CARD).status(PaymentStatus.READY)
                    .amount(10000).idempotencyKey("same-key").requestedAt(LocalDateTime.now()).expiresAt(deadline).build();
            payment.planGatewayCard(); payment.bindGatewayPayment(pgId); payments.saveAndFlush(payment);
            CheckoutAttempt attempt=CheckoutAttempt.prepare("same-key","owner","ORDER-1",eventId,Set.of(seatId),paymentNo,deadline);
            attempt.confirm(payment); attempts.saveAndFlush(attempt);
        });
        when(guard.requireBookable(anyLong(),anyBoolean())).thenReturn(events.findById(eventId).orElseThrow());
        when(cache.validateOccupiedSeat(anyLong(),anyString(),anyString(),anyList())).thenReturn(deadline);
        when(gateway.changeCardToBank(anyString(),any())).thenAnswer(inv->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            GatewayCardMethodChangeRequest request=inv.getArgument(1);
            GatewayCardMethodChangeRequest first=original.getAndSet(request);
            if(first!=null)assertThat(request).isEqualTo(first);
            assertThat(attempts.findById("same-key").orElseThrow().getGatewaySwitchBankCompany()).isEqualTo(BankCompany.KB);
            return switched(request);
        });
        when(gateway.issue(anyString(),any())).thenAnswer(inv->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Payment payment=payments.findByPaymentNo(paymentNo).orElseThrow();
            assertThat(payment.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(payment.getPgPaymentId()).isEqualTo(pgId);
            return new GatewayAccountIssueResponse(pgId,1L,paymentNo,"owner",BankCompany.KB,BankCompany.KB.getBankName(),
                    "ACCOUNT-1",new BigDecimal("10000"),payment.getGatewayExpiresAt(),"WAITING_DEPOSIT","WAITING_DEPOSIT");
        });
    }

    void initialBank(boolean bound) {
        reset(gateway);
        jdbc.update("update payments set method='BANK_TRANSFER', gateway_bank_company='KB', gateway_issue_pending=true, gateway_expires_at=? where payment_no=?", deadline.plusDays(1), paymentNo);
        if (!bound) jdbc.update("update payments set pg_payment_id=null where payment_no=?", paymentNo);
    }

    CheckoutConfirmRequest cardSwitchRequest() {
        return CheckoutConfirmRequest.builder().idempotencyKey("same-key").orderId("ORDER-1").eventId(eventId)
                .seats(List.of(SeatInfo.builder().id(seatId).zone("A").row(1).col(1).build())).paymentMethod(PaymentMethod.CREDIT_CARD).build();
    }

    dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeResponse cardSwitched(
            dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeRequest request) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return new dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeResponse(pgId,1L,paymentNo,"owner",
                PaymentMethod.CREDIT_CARD,new BigDecimal("10000"),request.expiresAt(),1);
    }

    @Test void unissuedBankSwitchesToCardAndManualRetryDoesNotIssueAnAccountOrPrepareNewPayment() {
        initialBank(true);
        when(gateway.changeBankToCard(eq(pgId),any())).thenAnswer(inv->cardSwitched(inv.getArgument(1)));
        PaymentResponse result=checkout.confirm("owner","queue",cardSwitchRequest());
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(result.getPgPaymentId()).isEqualTo(pgId);
        assertThat(checkout.confirm("owner","queue",cardSwitchRequest()).getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        verify(gateway,times(1)).changeBankToCard(eq(pgId),any());
        verify(gateway,never()).issue(anyString(),any());
        verify(gateway,never()).prepare(any());
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().isGatewayIssuePending()).isFalse();
    }

    @Test void lostBankSwitchResponsePreservesOriginalBankAndSameTargetOnRetry() {
        initialBank(true);
        java.util.concurrent.atomic.AtomicReference<dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeRequest> saved=new java.util.concurrent.atomic.AtomicReference<>();
        when(gateway.changeBankToCard(eq(pgId),any())).thenAnswer(inv->{saved.set(inv.getArgument(1));throw unavailable();});
        assertThatThrownBy(()->checkout.confirm("owner","queue",cardSwitchRequest())).isInstanceOf(dev.bum.ticket_service.exception.payment.GatewayMethodChangeUnconfirmedException.class);
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        doAnswer(inv->{assertThat(inv.getArgument(1,Object.class)).isEqualTo(saved.get());return cardSwitched(inv.getArgument(1));}).when(gateway).changeBankToCard(eq(pgId),any());
        assertThat(checkout.confirm("owner","queue",cardSwitchRequest()).getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    @Test void unboundBankPreparesWithOriginalBankTermsBeforeSwitchingToCard() {
        initialBank(false);
        when(gateway.prepare(any())).thenAnswer(inv->{
            GatewayPaymentPrepareRequest request=inv.getArgument(0);
            assertThat(request.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            return new GatewayPaymentPrepareResponse(pgId,1L,paymentNo,"owner",PaymentMethod.BANK_TRANSFER,BankCompany.KB,new BigDecimal("10000"),request.expiresAt(),"READY");
        });
        when(gateway.changeBankToCard(eq(pgId),any())).thenAnswer(inv->cardSwitched(inv.getArgument(1)));
        assertThat(checkout.confirm("owner","queue",cardSwitchRequest()).getPgPaymentId()).isEqualTo(pgId);
        verify(gateway,never()).issue(anyString(),any());
    }

    @Test void issuedOrPaidLocalBankCannotSwitchEvenIfBrowserClaimsFailure() {
        initialBank(true);
        jdbc.update("update payments set status='WAITING_DEPOSIT' where payment_no=?",paymentNo);
        assertThatThrownBy(()->checkout.confirm("owner","queue",cardSwitchRequest())).isInstanceOf(IllegalStateException.class);
        jdbc.update("update payments set status='PAID' where payment_no=?",paymentNo);
        assertThatThrownBy(()->checkout.confirm("owner","queue",cardSwitchRequest())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gateway);
    }

    @Test void bankSwitchRejectsWrongPgResponseWithoutChangingLocalMethod() {
        initialBank(true);
        when(gateway.changeBankToCard(eq(pgId),any())).thenReturn(new dev.bum.common.service.paymentgateway.payment.GatewayBankMethodChangeResponse(pgId,2L,paymentNo,"owner",PaymentMethod.CREDIT_CARD,new BigDecimal("10000"),deadline,1));
        assertThatThrownBy(()->checkout.confirm("owner","queue",cardSwitchRequest())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    CheckoutConfirmRequest request() {
        return CheckoutConfirmRequest.builder().idempotencyKey("same-key").orderId("ORDER-1").eventId(eventId)
                .seats(List.of(SeatInfo.builder().id(seatId).zone("A").row(1).col(1).build()))
                .paymentMethod(PaymentMethod.BANK_TRANSFER).bankCode("KB").build();
    }
    GatewayCardMethodChangeResponse switched(GatewayCardMethodChangeRequest request) {
        return new GatewayCardMethodChangeResponse("SWITCHED",pgId,1L,paymentNo,"owner",PaymentMethod.BANK_TRANSFER,
                new BigDecimal("10000"),BankCompany.KB,request.expiresAt(),1,null);
    }
    CardPaymentNotification approval() {
        return new CardPaymentNotification(1,pgId+":APPROVE:INITIAL",1L,pgId,paymentNo,"owner",PaymentMethod.CREDIT_CARD,
                PaymentStatus.PAID,new BigDecimal("10000"),"CARD-APPROVED",CardCompany.SHINHAN,"4111-****-****-1111",LocalDateTime.now());
    }
    FeignException unavailable() {
        return FeignException.errorStatus("switch",Response.builder().status(503).reason("unconfirmed")
                .request(Request.create(Request.HttpMethod.POST,"http://test",Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null)).build());
    }

    @Test void checkoutSwitchesTheExistingTransactionAndIssuesAnAccountAfterBothCommits() {
        PaymentResponse response=checkout.confirm("owner","queue",request());
        assertThat(response.getPgPaymentId()).isEqualTo(pgId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getAccountNumber()).isEqualTo("ACCOUNT-1");
        assertThat(checkout.confirm("owner","queue",request()).getPgPaymentId()).isEqualTo(pgId);
        verify(gateway,times(1)).changeCardToBank(anyString(),any());
        verify(gateway,never()).prepare(any());
    }

    @Test void unknownPgResultKeepsTheOriginalCardAndTargetForTheSameManualRetry() {
        doAnswer(inv->{original.set(inv.getArgument(1));throw unavailable();}).when(gateway).changeCardToBank(anyString(),any());
        assertThatThrownBy(()->checkout.confirm("owner","queue",request())).isInstanceOf(dev.bum.ticket_service.exception.payment.GatewayMethodChangeUnconfirmedException.class);
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        doAnswer(inv->{assertThat(inv.getArgument(1,GatewayCardMethodChangeRequest.class)).isEqualTo(original.get());return switched(inv.getArgument(1));})
                .when(gateway).changeCardToBank(anyString(),any());
        assertThat(checkout.confirm("owner","queue",request()).getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        verify(gateway,times(2)).changeCardToBank(eq(pgId),any());
    }

    @Test void successfulCardApprovalIsAppliedIdempotentlyAndNoAccountIsIssued() {
        CardPaymentNotification event=approval();
        doReturn(new GatewayCardMethodChangeResponse("APPROVED",pgId,1L,paymentNo,"owner",
                PaymentMethod.CREDIT_CARD,new BigDecimal("10000"),null,deadline,0,event)).when(gateway).changeCardToBank(anyString(),any());
        PaymentResponse response=checkout.confirm("owner","queue",request());
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.getCardTransactionId()).isEqualTo("CARD-APPROVED");
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(notifications.receive(event).outcome()).isEqualTo(CardPaymentNotificationResponse.Outcome.ACCEPTED);
        assertThat(checkout.confirm("owner","queue",request()).getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(gateway,times(1)).changeCardToBank(anyString(),any());
        verify(gateway,never()).issue(anyString(),any());
    }

    @Test void differentBankOwnerOrSeatsCannotReplaceTheCommittedIntent() {
        commands.prepareCardToBankChange("owner","queue",request());
        CheckoutConfirmRequest changed=request(); changed.setBankCode("NH");
        assertThatThrownBy(()->checkout.confirm("owner","queue",changed)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->checkout.confirm("other","queue",request())).isInstanceOf(AccessDeniedException.class);
        CheckoutConfirmRequest wrong=request(); wrong.setSeats(List.of(SeatInfo.builder().id(seatId+1).zone("A").row(1).col(2).build()));
        assertThatThrownBy(()->checkout.confirm("owner","queue",wrong)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test void invalidPgMetadataNeverChangesTheLocalMethodOrIssuesAnAccount() {
        doReturn(new GatewayCardMethodChangeResponse("SWITCHED",pgId,2L,paymentNo,"owner",
                PaymentMethod.BANK_TRANSFER,new BigDecimal("10000"),BankCompany.KB,deadline,1,null)).when(gateway).changeCardToBank(anyString(),any());
        assertThatThrownBy(()->checkout.confirm("owner","queue",request())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        verify(gateway,never()).issue(anyString(),any());
    }

    @Test void concurrentBankRetriesConvergeOnOneLocalMethod() throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(inv->{
            entered.countDown(); assertThat(release.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return switched(inv.getArgument(1));
        }).when(gateway).changeCardToBank(anyString(),any());
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<PaymentResponse> first=pool.submit(()->checkout.confirm("owner","queue",request()));
            java.util.concurrent.Future<PaymentResponse> second=pool.submit(()->checkout.confirm("owner","queue",request()));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS).getPgPaymentId()).isEqualTo(pgId);
            assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS).getPgPaymentId()).isEqualTo(pgId);
            assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(payments.count()).isEqualTo(1);
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void aLocalStorageFailureRetainsTheSameIntentAndRetryDoesNotPrepareAnotherPayment() {
        jdbc.execute("alter table payments add constraint method_switch_test_reject_bank check (method <> 'BANK_TRANSFER')");
        try {
            assertThatThrownBy(()->checkout.confirm("owner","queue",request())).isInstanceOf(RuntimeException.class);
        } finally {jdbc.execute("alter table payments drop constraint method_switch_test_reject_bank");}
        assertThat(payments.findByPaymentNo(paymentNo).orElseThrow().getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(attempts.findById("same-key").orElseThrow().getGatewaySwitchBankCompany()).isEqualTo(BankCompany.KB);
        verify(gateway,never()).issue(anyString(),any());
        assertThat(checkout.confirm("owner","queue",request()).getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        verify(gateway,times(2)).changeCardToBank(eq(pgId),any());
        verify(gateway,never()).prepare(any());
    }

    @Test void anUnboundCardIsPreparedWithItsOriginalCardTermsBeforeTheSwitch() {
        jdbc.update("update payments set pg_payment_id = null where payment_no = ?",paymentNo);
        when(gateway.prepare(any())).thenAnswer(inv->{
            GatewayPaymentPrepareRequest request=inv.getArgument(0);
            assertThat(request.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
            assertThat(request.expiresAt()).isEqualTo(deadline);
            return new GatewayPaymentPrepareResponse(pgId,1L,paymentNo,"owner",PaymentMethod.CREDIT_CARD,null,
                    request.amount(),request.expiresAt(),"READY");
        });
        assertThat(checkout.confirm("owner","queue",request()).getPgPaymentId()).isEqualTo(pgId);
        verify(gateway,times(1)).prepare(any());
        verify(gateway,times(1)).changeCardToBank(eq(pgId),any());
    }
}
