package dev.bum.ticket_service.service.checkout;

import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutCommandService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutService;

import dev.bum.ticket_service.service.payment.virtualAccount.*;
import dev.bum.ticket_service.service.checkout.payment.*;
import dev.bum.ticket_service.service.payment.notification.*;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.common.service.paymentgateway.notification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Retryer;
import org.springframework.cloud.openfeign.support.*;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.event.event.enums.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.*;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.payment.payment.*;
import dev.bum.ticket_service.service.payment.refund.*;
import dev.bum.ticket_service.service.reservation.reservation.ReservationCancellationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountIssuanceCheckoutIntegrationTest.PersistenceConfig.class)
@Import({VirtualAccountIssuanceCheckoutService.class, VirtualAccountIssuanceCheckoutCommandService.class,
        VirtualAccountExpirationScheduler.class, VirtualAccountExpirationCommandService.class,
        BankDepositNotificationService.class,
        BankCancellationNotificationService.class, PaymentService.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        ReservationCancellationService.class, TicketPurchaseCountService.class, TicketRepositoryImpl.class,
        VirtualAccountCancelOutboxService.class, VirtualAccountCancelOutboxPublisher.class,
        VirtualAccountCancelOutboxCommands.class,
        VirtualAccountCancelOutboxDispatcher.class, VirtualAccountPaymentLockService.class,
        VirtualAccountPaymentService.class, PaymentCompletionService.class, PaymentExpirationService.class,
        VirtualAccountReconciliationCompletionService.class, VirtualAccountReconciliationValidator.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualAccountIssuanceCheckoutIntegrationTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.ticket_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.ticket_service.jpa")
    static class PersistenceConfig { }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:ticksy-gateway-checkout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired ReservationCancellationService cancellation;
    @Autowired VirtualAccountPaymentService deposits;
    @Autowired VirtualAccountReconciliationCompletionService reconciliation;
    @Autowired VirtualAccountCancelOutboxPublisher publisher;
    @MockitoSpyBean VirtualAccountCancelOutboxCommands deliveryCommands;
    @MockitoBean dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayTransactionClient transactionGateway;
    @Autowired VirtualAccountCancelOutboxDispatcher dispatcher;
    @Autowired VirtualAccountCancelOutboxService enqueue;
    @MockitoSpyBean VirtualAccountCancelOutboxJpaRepository outbox;
    @Autowired ReservationJpaRepository reservations;
    @Autowired PaymentJpaRepository payments;
    @Autowired TicketJpaRepository tickets;
    @Autowired SeatJpaRepository seats;
    @Autowired EventJpaRepository events;
    @Autowired TicketPurchaseLockJpaRepository counts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean Clock clock;
    @MockitoBean SeatCacheService cache;
    @MockitoBean UserCouponRepository coupons;
    @MockitoBean PaymentRefundProcessService refunds;
    @MockitoBean PaymentGatewayVirtualAccountClient gateway;
    Fixture fixture;

    record Fixture(Long reservationId, Long paymentId, Long ticketId, Long seatId) { }

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-27T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        doAnswer(invocation -> {
            var request = invocation.getArgument(0, GatewayVirtualAccountCancelRequest.class);
            return new GatewayVirtualAccountCancelResponse(request.paymentNo(), request.bankName(),
                    request.accountNumber(), request.amount(), GatewayVirtualAccountStatus.CANCELLED);
        }).when(gateway).cancel(any());
        tx().executeWithoutResult(status -> {
            receipts.deleteAll();
            outbox.deleteAll();
            payments.deleteAll();
            tickets.deleteAll();
            reservations.deleteAll();
            seats.deleteAll();
            counts.deleteAll();
            events.deleteAll();
        });
        fixture = tx().execute(status -> {
            Event event = events.save(Event.builder().artistName("artist").title("event").venue("venue")
                    .eventDateTime(LocalDateTime.of(2026,11,1,18,0)).cancelDeadlineAt(LocalDateTime.of(2026,10,31,18,0))
                    .totalSeats(10).availableSeats(9).status(EventStatus.ON_SALE).maxTicketsPerPerson(4).build());
            Seat seat = seats.save(Seat.builder().event(event).zone("A").seatRow(1).seatCol(1)
                    .grade(SeatGrade.VIP).price(10000).status(SeatStatus.LOCKED).build());
            Reservation reservation = reservations.save(Reservation.builder().event(event).userId("owner")
                    .orderId("ORDER-1").status(ReservationStatus.PENDING_PAYMENT).reservedAt(LocalDateTime.now()).build());
            Ticket ticket = tickets.save(Ticket.builder().event(event).reservation(reservation).userId("owner")
                    .seat(seat).status(TicketStatus.PENDING_PAYMENT).build());
            Payment payment = Payment.builder().reservation(reservation).paymentNo("PAY-1")
                    .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.WAITING_DEPOSIT).amount(10000)
                    .idempotencyKey("checkout-1").requestedAt(LocalDateTime.now()).build();
            payment.waitDeposit(BankCompany.KB.getBankName(), "1111-123456", LocalDateTime.of(2026,10,28,23,59));
            payments.saveAndFlush(payment);
            jdbc.update("insert into ticket_purchase_locks(user_id, limit_scope, scope_key, ticket_count, created_at) values (?, ?, ?, ?, ?)",
                    "owner", "PER_EVENT", event.getEventId().toString(), 1, LocalDateTime.now());
            return new Fixture(reservation.getReservationId(), payment.getPaymentId(), ticket.getTicketId(), seat.getSeatId());
        });
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private void cancelWithinTransaction() {
        cancellation.cancel(reservations.findByIdForUpdate(fixture.reservationId()).orElseThrow(),
                CancelReservationRequest.builder().selectedTicketIdList(List.of(fixture.ticketId())).build());
    }
    private void cancel() { tx().executeWithoutResult(status -> cancelWithinTransaction()); }
    private VirtualAccountCancelOutbox record() { return outbox.findByPaymentNo("PAY-1").orElseThrow(); }
    private void makeReady() { jdbc.update("update virtual_account_cancel_outbox set next_attempt_at = ?", LocalDateTime.now().minusMinutes(1)); }
    private void assertCancelled() {
        tx().executeWithoutResult(status -> {
            assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);
            assertThat(counts.findAll().getFirst().getTicketCount()).isZero();
        });
    }

    @Autowired VirtualAccountIssuanceCheckoutService issuance;
    @Autowired VirtualAccountExpirationScheduler expirationScheduler;
    @Autowired VirtualAccountExpirationCommandService expirationCommands;
    @Autowired PaymentExpirationService localExpiration;
    @Autowired BankDepositNotificationService notifications;
    @Autowired BankCancellationNotificationService results;
    @MockitoSpyBean GatewayBankCancellationReceiptJpaRepository receipts;
    @MockitoBean CardPaymentService cards;
    @Autowired ObjectMapper json;
    final String pgId = "20261111550e8400-e29b-41d4-a716-446655440000";
    final LocalDateTime deadline = LocalDateTime.of(2026,10,28,23,59);
    final List<String> prepareBodies = new CopyOnWriteArrayList<>();
    final List<String> issueBodies = new CopyOnWriteArrayList<>();
    final List<String> cancelBodies = new CopyOnWriteArrayList<>();
    final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    HttpServer server;
    ExecutorService httpWorkers;
    GatewayPaymentPrepareRequest first;
    boolean losePrepare, loseIssue, wrongAmount, wrongIssue, loseCancel;
    boolean alwaysFailPrepare, alwaysFailIssue;
    int prepareFailureStatus = 503;
    CountDownLatch issued, releaseIssue;
    Runnable beforeCancelResponse;
    String immediateCancellationOutcome;
    String queryStatus = "WAITING_DEPOSIT";
    int queryStatusCode = 200;
    boolean wrongQueryMerchant;
    final AtomicInteger queryCount = new AtomicInteger();
    Runnable beforeStatusResponse;

    @BeforeEach void startHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpWorkers = Executors.newCachedThreadPool(); server.setExecutor(httpWorkers);
        server.createContext("/", this::receive); server.start();
        Client http = new Client.Default(null, null);
        PaymentGatewayTransactionClient client = Feign.builder().contract(new SpringMvcContract())
                .client((request, options) -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return http.execute(request, options);
                }).retryer(Retryer.NEVER_RETRY)
                .requestInterceptor(new dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig()
                        .paymentGatewayMerchantRequestInterceptor(1L, "ticksy_test-merchant-token"))
                .encoder((value, type, template) -> {
                    try {
                        template.header("Content-Type", "application/json");
                        template.body(json.writeValueAsBytes(value), StandardCharsets.UTF_8);
                    } catch (IOException e) { throw new IllegalStateException(e); }
                })
                .decoder(new ResponseEntityDecoder((response, type) ->
                        json.readValue(response.body().asInputStream(), json.constructType(type))))
                .target(PaymentGatewayTransactionClient.class, "http://127.0.0.1:" + server.getAddress().getPort());
        lenient().when(transactionGateway.prepare(any())).thenAnswer(call -> client.prepare(call.getArgument(0)));
        lenient().when(transactionGateway.issue(anyString(), any())).thenAnswer(call -> client.issue(call.getArgument(0), call.getArgument(1)));
        lenient().when(transactionGateway.cancel(anyString(), any())).thenAnswer(call -> client.cancel(call.getArgument(0), call.getArgument(1)));
        lenient().when(transactionGateway.findByPgPaymentId(anyString())).thenAnswer(call -> client.findByPgPaymentId(call.getArgument(0)));
        lenient().when(transactionGateway.findByPaymentNo(anyString())).thenAnswer(call -> client.findByPaymentNo(call.getArgument(0)));
    }

    @AfterEach void stop() {
        if (releaseIssue != null) releaseIssue.countDown();
        server.stop(0); httpWorkers.shutdownNow();
        assertThat(serverFailure.get()).isNull();
    }

    private void plan() {
        tx().executeWithoutResult(status -> {
            Payment payment = payments.findByPaymentNoForUpdate("PAY-1").orElseThrow();
            payment.planGatewayAccount(BankCompany.KB, deadline);
        });
        jdbc.update("update payments set status = 'READY'");
    }

    @Test void realHttpUsesPrepareThenIssueAndReplayDoesNotCreateAnotherAccountOrHistory() {
        plan(); PaymentResponse response = issuance.issue("PAY-1");
        assertThat(response.getPgPaymentId()).isEqualTo(pgId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getAccountNumber()).isEqualTo("1111-123456");
        long history = jdbc.queryForObject("select count(*) from payment_histories", Long.class);
        assertThat(issuance.issue("PAY-1")).isEqualTo(response);
        assertThat(prepareBodies).hasSize(1); assertThat(issueBodies).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_histories", Long.class)).isEqualTo(history);
        verifyNoInteractions(gateway);
    }

    @Test void lostPrepareResponseIsRetriedWithinTheSameRequestWithExactlyTheSameTerms() {
        plan(); losePrepare = true;
        issuance.issue("PAY-1");
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(prepareBodies).hasSize(2); assertThat(prepareBodies.get(0)).isEqualTo(prepareBodies.get(1));
        assertThat(first.paymentNo()).isEqualTo("PAY-1");
    }

    @Test void lostIssueResponseKeepsPgIdAndRetriesTheSameIssueWithoutAnotherPrepare() {
        plan(); loseIssue = true;
        assertThat(issuance.issue("PAY-1").getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId);
        assertThat(payment().isGatewayIssuePending()).isFalse();
        assertThat(prepareBodies).hasSize(1); assertThat(issueBodies).hasSize(2);
        assertThat(issueBodies.get(0)).isEqualTo(issueBodies.get(1));
    }

    @Test void allFourPrepareAttemptsFailBeforeBrowserRetryReusesTheSamePaymentAndTerms() {
        plan(); alwaysFailPrepare = true;
        assertThatThrownBy(() -> issuance.issue("PAY-1"))
                .isInstanceOf(dev.bum.ticket_service.exception.payment.GatewayAccountIssueUnconfirmedException.class);
        assertThat(prepareBodies).hasSize(4); assertThat(issueBodies).isEmpty();
        assertThat(payment().getPgPaymentId()).isNull(); assertThat(payment().isGatewayIssuePending()).isTrue();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(payments.count()).isEqualTo(1);
        alwaysFailPrepare = false;
        assertThat(issuance.issue("PAY-1").getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(prepareBodies).hasSize(5);
        assertThat(prepareBodies.stream().distinct()).hasSize(1);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test void allFourIssueAttemptsKeepTheCommittedPgIdForTheNextBrowserRequest() {
        plan(); alwaysFailIssue = true;
        assertThatThrownBy(() -> issuance.issue("PAY-1"))
                .isInstanceOf(dev.bum.ticket_service.exception.payment.GatewayAccountIssueUnconfirmedException.class);
        assertThat(prepareBodies).hasSize(1); assertThat(issueBodies).hasSize(4);
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId);
        assertThat(payment().getAccountNumber()).isNull(); assertThat(payment().isGatewayIssuePending()).isTrue();
        alwaysFailIssue = false;
        assertThat(issuance.issue("PAY-1").getAccountNumber()).isEqualTo("1111-123456");
        assertThat(prepareBodies).hasSize(1); assertThat(issueBodies).hasSize(5);
        assertThat(issueBodies.stream().distinct()).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 409})
    void permanentPgRejectionsAreNotRetried(int status) {
        plan(); alwaysFailPrepare = true; prepareFailureStatus = status;
        assertThatThrownBy(() -> issuance.issue("PAY-1")).isInstanceOf(FeignException.class);
        assertThat(prepareBodies).hasSize(1); assertThat(issueBodies).isEmpty();
    }

    @Test void changedAmountFromPrepareOrIssueCannotBindAnotherTransaction() {
        plan(); wrongAmount = true;
        assertThatThrownBy(() -> issuance.issue("PAY-1")).hasMessageContaining("PG 준비 결과");
        assertThat(payment().getPgPaymentId()).isNull(); assertThat(issueBodies).isEmpty();
        wrongAmount = false; wrongIssue = true;
        assertThatThrownBy(() -> issuance.issue("PAY-1")).hasMessageContaining("PG 발급 결과");
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId); assertThat(payment().getAccountNumber()).isNull();
        wrongIssue = false;
        assertThat(issuance.issue("PAY-1").getAccountNumber()).isEqualTo("1111-123456");
    }

    @Test void cancellationBeforePrepareIsCommittedAndIssuanceRecoveryConnectsTheCleanupOutbox() {
        plan(); cancel(); assertCancelled(); assertThat(record().getAccountNumber()).isNull();
        alwaysFailPrepare = true;
        publisher.deliver(record().getOutboxId());
        assertThat(prepareBodies).hasSize(4);
        assertThat(cancelBodies).isEmpty(); assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        alwaysFailPrepare = false;
        makeReady(); publisher.deliver(record().getOutboxId());
        assertCancelled(); assertThat(record().getPgPaymentId()).isEqualTo(pgId);
        assertThat(record().getAccountNumber()).isEqualTo("1111-123456");
        assertThat(record().getGatewayResult()).isEqualTo("PROCESSING");
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
        assertThat(results.receive(cancellationEvent("CANCELLED")).outcome()).isEqualTo("CANCELLED");
        assertThat(record().getGatewayResult()).isEqualTo("CANCELLED"); assertCancelled();
    }

    @Test void cancellationCanCommitWhileIssueHttpIsWaitingAndLateResponseCannotReviveTheReservation() throws Exception {
        plan(); issued = new CountDownLatch(1); releaseIssue = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<PaymentResponse> result = workers.submit(() -> issuance.issue("PAY-1"));
            await(issued); cancel(); assertCancelled(); releaseIssue.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }
        assertThat(record().getAccountNumber()).isEqualTo("1111-123456"); assertCancelled();
    }

    @Test void depositNotificationBeforeIssueResponseBindsTheAccountAndDoesNotDowngradePaid() throws Exception {
        plan(); issued = new CountDownLatch(1); releaseIssue = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<PaymentResponse> result = workers.submit(() -> issuance.issue("PAY-1"));
            await(issued); BankDepositNotification event = depositEvent();
            assertThat(notifications.receive(event).outcome()).isEqualTo(BankDepositNotificationResponse.Outcome.APPLIED);
            long history = jdbc.queryForObject("select count(*) from payment_histories", Long.class);
            assertThat(notifications.receive(event).matches(event)).isTrue(); releaseIssue.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(jdbc.queryForObject("select count(*) from payment_histories", Long.class)).isEqualTo(history);
        }
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
    }

    @Test void finalCallbackBefore202AndLostCallbackAcknowledgementKeepTheFirstFinancialResult() {
        plan(); issuance.issue("PAY-1"); cancel(); BankCancellationNotification event = cancellationEvent("CANCELLED");
        beforeCancelResponse = () -> results.receive(event); publisher.deliver(record().getOutboxId());
        assertThat(record().getGatewayResult()).isEqualTo("CANCELLED");
        assertThat(results.receive(event).matches(event)).isTrue(); assertThat(receipts.count()).isEqualTo(1);
        deliveryCommands.failed(record().getOutboxId(), record().getDeliveryAttempt(), "LATE_HTTP_FAILURE", LocalDateTime.now());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
        assertThat(record().getGatewayResult()).isEqualTo("CANCELLED"); assertCancelled();
    }

    @Test void lostCancellationResponseRetriesTheSameRequestAndRejectionDoesNotReopenSeats() {
        plan(); issuance.issue("PAY-1"); cancel(); loseCancel = true; publisher.deliver(record().getOutboxId());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        makeReady(); publisher.deliver(record().getOutboxId());
        assertThat(cancelBodies).hasSize(2); assertThat(cancelBodies.get(0)).isEqualTo(cancelBodies.get(1));
        BankCancellationNotification event = cancellationEvent("REJECTED");
        assertThat(results.receive(event).matches(event)).isTrue();
        assertThat(record().getGatewayResult()).isEqualTo("REJECTED"); assertCancelled();
    }

    @Test void receiptFailureRollsBackTheOutboxResultAndTheSameNotificationCanBeRetried() {
        plan(); issuance.issue("PAY-1"); cancel(); publisher.deliver(record().getOutboxId());
        doThrow(new IllegalStateException("receipt failure")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> results.receive(cancellationEvent("CANCELLED"))).hasMessage("receipt failure");
        assertThat(record().getGatewayResult()).isEqualTo("PROCESSING"); assertThat(receipts.count()).isZero();
        reset(receipts); assertThat(results.receive(cancellationEvent("CANCELLED")).outcome()).isEqualTo("CANCELLED");
    }

    @Test void cancellationOutboxFailureRollsBackLateAccountBindingAndRetryCompletesIt() {
        plan(); cancel(); doThrow(new IllegalStateException("outbox failure")).when(outbox).findIdByPaymentNo("PAY-1");
        assertThatThrownBy(() -> issuance.issue("PAY-1")).hasMessage("outbox failure");
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId);
        assertThat(payment().getAccountNumber()).isNull(); assertThat(payment().isGatewayIssuePending()).isTrue();
        reset(outbox); issuance.issue("PAY-1");
        assertThat(record().getAccountNumber()).isEqualTo("1111-123456"); assertCancelled();
    }

    @Test void anotherPgIdCannotCompleteTheLocalPaymentAndAmbientTransactionsAreRejected() {
        plan(); issuance.issue("PAY-1"); BankDepositNotification a = depositEvent();
        String other = "20261111550e8400-e29b-41d4-a716-446655440001";
        BankDepositNotification event = new BankDepositNotification(1, other + ":DEPOSIT:INITIAL", 1L, other,
                "PAY-1", "owner", PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, a.amount(),
                a.accountNumber(), BankCompany.KB, a.depositorName(), a.depositedAt());
        assertThatThrownBy(() -> notifications.receive(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThatThrownBy(() -> tx().execute(status -> issuance.issue("PAY-1")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void paidLinkedPaymentCannotUseLegacyReservationRefundAndRetainsItsTickets() {
        plan(); issuance.issue("PAY-1"); notifications.receive(depositEvent());
        assertThatThrownBy(this::cancel)
                .hasMessage("결제 완료된 예매의 환불은 환불 신청 API를 이용해주세요.");
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(outbox.count()).isZero();
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CANCELLED", "REJECTED"})
    void immediateFinalCancellationResponseIsStoredAndItsLaterCallbackMatches(String outcome) {
        plan(); issuance.issue("PAY-1"); cancel(); immediateCancellationOutcome = outcome;
        publisher.deliver(record().getOutboxId());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
        assertThat(record().getGatewayResult()).isEqualTo(outcome);
        assertThat(receipts.count()).isZero();
        BankCancellationNotification event = cancellationEvent(outcome);
        assertThat(results.receive(event).matches(event)).isTrue();
        assertThat(record().getGatewayResult()).isEqualTo(outcome);
        assertThat(receipts.count()).isEqualTo(1); assertCancelled();
    }

    @Test void aPastLocalDeadlineDoesNotExpireWhilePgIsWaitingOrAlreadyPaidAndDelayedDepositStillCompletes() {
        plan(); issuance.issue("PAY-1"); movePastDeadline();
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        queryStatus = "PAID";
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(notifications.receive(depositEvent()).outcome()).isEqualTo(BankDepositNotificationResponse.Outcome.APPLIED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
        expirationScheduler.expirePayments();
        assertThat(queryCount).hasValue(2);
    }

    @Test void onlyPgExpiredRunsTheExistingAtomicReservationTicketPaymentAndSeatCleanupOnce() {
        plan(); issuance.issue("PAY-1"); movePastDeadline(); queryStatus = "EXPIRED";
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.EXPIRED);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(counts.findAll().getFirst().getTicketCount()).isZero();
        assertThat(outbox.count()).isZero();
        long history = jdbc.queryForObject("select count(*) from payment_histories", Long.class);
        expirationScheduler.expirePayments();
        assertThat(queryCount).hasValue(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_histories", Long.class)).isEqualTo(history);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {404, 503})
    void pgLookupFailureCannotReleaseTheReservationOrSeats(int status) {
        plan(); issuance.issue("PAY-1"); movePastDeadline(); queryStatusCode = status;
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.LOCKED);
        queryStatusCode = 200; queryStatus = "EXPIRED";
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test void readyLinkedPaymentsCannotBeExpiredByTheLegacyLocalClockAndMissingPgIdUsesScopedLookup() {
        plan(); movePastDeadline();
        localExpiration.expireIfExpired(fixture.paymentId());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payments.findExpiredPaymentIds(List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT),
                PaymentMethod.BANK_TRANSFER, PaymentStatus.WAITING_DEPOSIT, deadline.plusDays(1), org.springframework.data.domain.PageRequest.of(0, 100))).doesNotContain(fixture.paymentId());
        expirationScheduler.expirePayments();
        verify(transactionGateway).findByPaymentNo("PAY-1");
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.READY);
        queryStatus = "EXPIRED";
        expirationScheduler.expirePayments();
        assertThat(payment().getPgPaymentId()).isEqualTo(pgId);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment().isGatewayIssuePending()).isFalse();
        assertThat(prepareBodies).isEmpty(); assertThat(issueBodies).isEmpty();
    }

    @Test void anotherMerchantsExpiredResultCannotExpireTheLocalTransaction() {
        plan(); issuance.issue("PAY-1"); movePastDeadline(); queryStatus = "EXPIRED"; wrongQueryMerchant = true;
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.LOCKED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aLateExpiredLookupCannotOverrideADepositOrCancellationThatCommittedDuringHttp(boolean paid) {
        plan(); issuance.issue("PAY-1"); movePastDeadline(); queryStatus = "EXPIRED";
        beforeStatusResponse = paid ? () -> notifications.receive(depositEvent()) : this::cancel;
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(paid ? PaymentStatus.PAID : PaymentStatus.CANCELLED);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus())
                .isEqualTo(paid ? SeatStatus.RESERVED : SeatStatus.AVAILABLE);
    }

    @Test void aLocalCleanupFailureRollsBackEverythingAndTheNextPgQueryCanRetryIt() {
        plan(); issuance.issue("PAY-1"); movePastDeadline(); queryStatus = "EXPIRED";
        doThrow(new IllegalStateException("cache sync registration failed")).when(cache)
                .syncAvailableSeatsAfterCommit(anyList(), anyString(), anyString());
        expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(counts.findAll().getFirst().getTicketCount()).isEqualTo(1);
        reset(cache); expirationScheduler.expirePayments();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test void anOldKafkaExpirationCannotBypassPgConfirmationForLinkedPayments() {
        plan(); issuance.issue("PAY-1"); movePastDeadline();
        assertThatThrownBy(() -> deposits.expireFromGateway(VirtualAccountExpiredEvent.builder().paymentNo("PAY-1")
                .amount(BigDecimal.valueOf(10000)).bankCompany(BankCompany.KB).bankName(BankCompany.KB.getBankName())
                .accountNumber("1111-123456").expiredAt(deadline.plusSeconds(1)).build()))
                .hasMessageContaining("PG의 확정 상태 조회");
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
    }

    private void movePastDeadline() {
        when(clock.instant()).thenReturn(deadline.plusMinutes(2).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }

    private void receive(HttpExchange exchange) throws IOException {
        try {
            assertThat(exchange.getRequestHeaders().getFirst("Merchant")).isEqualTo("1");
            assertThat(exchange.getRequestHeaders().getFirst("credentialKey")).isEqualTo("ticksy_test-merchant-token");
            assertThat(exchange.getRequestHeaders().getFirst("X-Service-Token")).isNull();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/v1/payments/gateway/" + pgId)
                    || path.equals("/api/v1/payments/gateway/by-payment-no/PAY-1")) {
                queryCount.incrementAndGet();
                if (queryStatusCode != 200) { send(exchange, queryStatusCode, Map.of()); return; }
                GatewayPaymentPrepareResponse snapshot = new GatewayPaymentPrepareResponse(pgId,
                        wrongQueryMerchant ? 2L : 1L, "PAY-1", "owner", PaymentMethod.BANK_TRANSFER,
                        BankCompany.KB, BigDecimal.valueOf(10000), deadline, queryStatus);
                if (beforeStatusResponse != null) beforeStatusResponse.run();
                send(exchange, 200, snapshot);
            } else if (path.equals("/api/v1/payments/gateway/prepare")) {
                prepareBodies.add(body); first = json.readValue(body, GatewayPaymentPrepareRequest.class);
                if (alwaysFailPrepare || (losePrepare && prepareBodies.size() == 1)) {
                    send(exchange, prepareFailureStatus, Map.of()); return;
                }
                send(exchange, 200, new GatewayPaymentPrepareResponse(pgId, 1L, first.paymentNo(), first.buyerReference(),
                        first.paymentMethod(), first.bankCompany(), wrongAmount ? BigDecimal.ONE : first.amount(), first.expiresAt(), "READY"));
            } else if (path.equals("/api/v1/payments/virtual-account/gateway/" + pgId + "/issue")) {
                assertThat(payment().getPgPaymentId()).isEqualTo(pgId); issueBodies.add(body);
                if (issued != null) { issued.countDown(); await(releaseIssue); }
                if (alwaysFailIssue || (loseIssue && issueBodies.size() == 1)) { send(exchange, 503, Map.of()); return; }
                send(exchange, 200, new GatewayAccountIssueResponse(pgId, 1L, "PAY-1", "owner", BankCompany.KB,
                        BankCompany.KB.getBankName(), "1111-123456", wrongIssue ? BigDecimal.ONE : BigDecimal.valueOf(10000),
                        deadline, "WAITING_DEPOSIT", "WAITING_DEPOSIT"));
            } else if (path.equals("/api/v1/payments/virtual-account/gateway/" + pgId + "/cancel")) {
                cancelBodies.add(body); if (beforeCancelResponse != null) beforeCancelResponse.run();
                if (loseCancel && cancelBodies.size() == 1) { send(exchange, 503, Map.of()); return; }
                if (immediateCancellationOutcome != null) {
                    BankCancellationNotification result = cancellationEvent(immediateCancellationOutcome);
                    send(exchange, 200, new GatewayAccountCancelResponse(pgId, "PAY-1", pgId + ":CANCEL:INITIAL",
                            result.outcome(), result.reason(), result.outcome().equals("CANCELLED") ? "CANCELLED" : "PAID",
                            result.cancelledAt(), false));
                    return;
                }
                send(exchange, 202, new GatewayAccountCancelResponse(pgId, "PAY-1", pgId + ":CANCEL:INITIAL",
                        "PROCESSING", null, "WAITING_DEPOSIT", null, false));
            } else { throw new IllegalStateException("Unexpected path: " + path); }
        } catch (Throwable failure) { serverFailure.set(failure); send(exchange, 500, Map.of()); }
        finally { exchange.close(); }
    }
    private void send(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes);
    }
    private BankDepositNotification depositEvent() {
        return new BankDepositNotification(1, pgId + ":DEPOSIT:INITIAL", 1L, pgId, "PAY-1", "owner",
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, BigDecimal.valueOf(10000), "1111-123456", BankCompany.KB,
                "입금자", LocalDateTime.of(2026,10,27,12,0));
    }
    private BankCancellationNotification cancellationEvent(String outcome) {
        return new BankCancellationNotification(1, pgId + ":CANCEL:INITIAL", 1L, pgId, "PAY-1", "owner",
                PaymentMethod.BANK_TRANSFER, BigDecimal.valueOf(10000), "1111-123456", BankCompany.KB, outcome,
                outcome.equals("CANCELLED") ? "PG_MERCHANT_CANCELLED" : "ALREADY_DEPOSITED",
                outcome.equals("CANCELLED") ? LocalDateTime.of(2026,11,11,12,0) : null, false);
    }
    private Payment payment() { return payments.findById(fixture.paymentId()).orElseThrow(); }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
