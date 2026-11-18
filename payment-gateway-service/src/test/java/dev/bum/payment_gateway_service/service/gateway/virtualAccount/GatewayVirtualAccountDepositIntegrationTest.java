package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.config.*;
import dev.bum.payment_gateway_service.controller.advice.*;
import dev.bum.payment_gateway_service.controller.financial.BankController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayPaymentController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.controller.gateway.deposit.GatewayBankEventController;
import dev.bum.payment_gateway_service.controller.gateway.expiration.GatewayBankExpirationEventController;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import dev.bum.payment_gateway_service.service.gateway.notification.*;
import dev.bum.payment_gateway_service.service.gateway.payment.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayVirtualAccountDepositIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-deposit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.ticket.service-token=merchant-a", "app.payment-provider.token=bank-provider"
})
@AutoConfigureMockMvc
class GatewayVirtualAccountDepositIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    @Import({
            dev.bum.payment_gateway_service.service.outbox.BankTransferOutboxCommands.class,
            BankDepositNotificationClient.class, BankDepositOutboxDispatcher.class, GatewayBankEventController.class,
            BankVirtualAccountExpirationService.class, BankVirtualAccountExpirationScheduler.class,
            BankExpirationNotificationClient.class, BankExpirationOutboxDispatcher.class,
            GatewayVirtualAccountExpirationCommands.class, GatewayBankExpirationEventController.class,
            GatewayBankCancellationDispatcher.class,dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancellationService.class,
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancelCommands.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountCancellationService.class,
            SecurityConfig.class, PaymentProviderTokenValidator.class,
            MerchantAuthenticationService.class, FinancialSimulationHttpConfig.class, FinancialSimulationGatewayService.class, GatewayPaymentService.class,
            GatewayPaymentCommandService.class, GatewayVirtualAccountIssuanceService.class,
            GatewayVirtualAccountIssueCommandService.class, BankVirtualAccountIssuanceService.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankAccountIssueGateCreator.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankAccountIssueGateAccess.class,
            BankVirtualAccountIssueCommandService.class, BankVirtualAccountDepositService.class,
            GatewayVirtualAccountDepositCommands.class,
            GatewayPaymentController.class, GatewayVirtualAccountController.class, BankController.class, dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountRefundService.class,
            GlobalExceptionHandler.class, FinancialSimulationExceptionHandler.class,
            MerchantNotificationTargetResolver.class, MerchantNotificationTokenCipher.class,
            MerchantNotificationEndpointService.class, GatewayBankNotificationCommands.class,
            GatewayBankNotificationDispatcher.class, StandardBankNotificationAdapter.class})
    static class Config { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MerchantJpaRepository merchants;
    @Autowired MerchantNotificationEndpointJpaRepository endpoints;
    @Autowired MerchantNotificationEndpointService endpointService;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired BankVirtualAccountDepositService bank;
    @Autowired GatewayVirtualAccountDepositCommands depositCommands;
    @Autowired BankDepositOutboxDispatcher bankDispatcher;
    @Autowired BankVirtualAccountExpirationService expirations;
    @Autowired BankVirtualAccountExpirationScheduler expirationScheduler;
    @Autowired BankExpirationOutboxDispatcher expirationDispatcher;
    @Autowired GatewayVirtualAccountExpirationCommands expirationCommands;
    @Autowired FinancialSimulationToken financialToken;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean BankExpirationNotificationClient expirationClient;
    @MockitoSpyBean dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutboxJpaRepository bankOutboxes;
    @MockitoSpyBean BankDepositNotificationClient bankClient;
    @Autowired BankVirtualAccountCancellationJpaRepository bankCancellations;
    @Autowired GatewayBankNotificationDispatcher dispatcher;
    @Autowired GatewayBankNotificationCommands delivery;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @MockitoSpyBean GatewayBankNotificationJpaRepository notifications;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @MockitoSpyBean StandardBankNotificationAdapter adapter;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;

    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 23, 59, 59);
    final LocalDateTime depositedAt = LocalDateTime.of(2026, 11, 9, 12, 0, 0, 123456000);
    final Queue<BankDepositNotification> received = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> mode = new AtomicReference<>("APPLIED");
    HttpServer merchantServer;
    Merchant first;
    Merchant second;

    @BeforeEach
    void setup() throws Exception {
        bankOutboxes.deleteAll();
        bankCancellations.deleteAll();
        notifications.deleteAll();
        bankHistories.deleteAll();
        accounts.deleteAll();
        histories.deleteAll();
        payments.deleteAll();
        endpoints.deleteAll();
        merchants.deleteAll();
        first = merchant("ticksy");
        second = merchant("melon");
        received.clear();
        mode.set("APPLIED");
        merchantServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        merchantServer.createContext("/", exchange -> {
            BankDepositNotification event = json.readValue(exchange.getRequestBody(), BankDepositNotification.class);
            received.add(event);
            if (!exchange.getRequestURI().getPath().equals("/" + event.merchantId() + "/hook")
                    || !Objects.equals("notify-" + event.merchantId(), exchange.getRequestHeaders().getFirst("X-Service-Token"))
                    || mode.get().equals("ERROR")) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            BankDepositNotificationResponse response = BankDepositNotificationResponse.of(event,
                    mode.get().equals("CANCELLED") ? BankDepositNotificationResponse.Outcome.RESERVATION_CANCELLED
                            : BankDepositNotificationResponse.Outcome.APPLIED);
            if (mode.get().equals("BAD_ACK")) {
                response = new BankDepositNotificationResponse(response.version(), "OTHER-EVENT", response.merchantId(),
                        response.pgPaymentId(), response.merchantPaymentNo(), response.accountNumber(), response.amount(), response.outcome());
            }
            byte[] bytes = json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        merchantServer.start();
        for (Merchant merchant : List.of(first, second)) {
            endpointService.register(merchant.getMerchantId(), "default", "http://127.0.0.1:"
                    + merchantServer.getAddress().getPort() + "/" + merchant.getMerchantId() + "/hook",
                    "notify-" + merchant.getMerchantId());
        }
    }

    @AfterEach
    void stop() { merchantServer.stop(0); }

    @Test
    void depositCommitsBankHistoryAndPgPaymentHistoryAndMerchantNotification() throws Exception {
        String id = issue("merchant-a", "PAY-DEPOSIT");
        deposit(id).andExpect(status().isOk()).andExpect(jsonPath("$.pgPaymentId").value(id))
                .andExpect(jsonPath("$.accountNumber").value(request(id).getAccountNumber()))
                .andExpect(jsonPath("$.status").value("DEPOSITED")).andExpect(jsonPath("$.pgStatus").doesNotExist())
                .andExpect(jsonPath("$.notificationStatus").doesNotExist());
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isEqualTo(depositedAt);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(notifications.count()).isEqualTo(1);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(received.peek().merchantId()).isEqualTo(first.getMerchantId());
    }

    @Test
    void differentMerchantsCanDepositTheSamePaymentNumberToTheirOwnTargets() throws Exception {
        String a = issue("merchant-a", "SAME-NO");
        String b = issue("merchant-b", "SAME-NO");
        deposit(a).andExpect(status().isOk());
        deposit(b).andExpect(status().isOk());
        dispatcher.retryPending();
        assertThat(received).extracting(BankDepositNotification::merchantId)
                .containsExactlyInAnyOrder(first.getMerchantId(), second.getMerchantId());
        assertThat(received).extracting(BankDepositNotification::pgPaymentId).containsExactlyInAnyOrder(a, b);
        assertThat(received).allMatch(event -> event.merchantPaymentNo().equals("SAME-NO"));
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void duplicateDepositsKeepTheFirstTimestampAndCreateOneHistoryAndEvent() throws Exception {
        String id = issue("merchant-a", "DUPLICATE");
        deposit(id).andExpect(status().isOk());
        GatewayVirtualAccountDepositRequest retry = request(id);
        retry.setDepositedAt(null);
        postDeposit(retry).andExpect(status().isOk());
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(event(id).getDepositedAt()).isEqualTo(depositedAt);
    }

    @Test
    void differentAmountNameOrTimestampOnTheSameDepositIsRejected() throws Exception {
        String id = issue("merchant-a", "MISMATCH");
        deposit(id).andExpect(status().isOk());
        GatewayVirtualAccountDepositRequest amount = request(id);
        amount.setAmount(new BigDecimal("101"));
        postDeposit(amount).andExpect(status().isConflict());
        GatewayVirtualAccountDepositRequest name = request(id);
        name.setDepositorName("OTHER");
        postDeposit(name).andExpect(status().isConflict());
        GatewayVirtualAccountDepositRequest time = request(id);
        time.setDepositedAt(depositedAt.plusSeconds(1));
        postDeposit(time).andExpect(status().isConflict());
        assertThat(event(id).getDepositorName()).isEqualTo("입금자");
        assertThat(bankHistories.count()).isEqualTo(2);
    }

    @Test
    void concurrentDuplicatesSerializeOnTheBankAccountAndPgPayment() throws Exception {
        String id = issue("merchant-a", "CONCURRENT");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> deposit(id).andReturn().getResponse().getStatus());
            Future<Integer> b = pool.submit(() -> deposit(id).andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsOnly(200);
        } finally { pool.shutdownNow(); }
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void bankHistoryStorageFailureRollsBackTheBankDeposit() throws Exception {
        String id = issue("merchant-a", "BANK-ROLLBACK");
        doThrow(new DataIntegrityViolationException("simulated")).when(bankHistories)
                .saveAndFlush(argThat(history -> history.getHistoryType() == VirtualAccountPaymentHistoryType.DEPOSITED));
        deposit(id).andExpect(status().isServiceUnavailable());
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isNull();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(notifications.count()).isZero();
    }

    @Test
    void pgOutboxFailureRollsBackPgButRecoveryUsesTheCommittedBankDeposit() throws Exception {
        String id = issue("merchant-a", "PG-OUTBOX-ROLLBACK");
        doThrow(new DataIntegrityViolationException("simulated")).when(notifications).saveAndFlush(any());
        deposit(id).andExpect(status().isOk());
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isNull();
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        assertThat(notifications.count()).isZero();
        reset(notifications);
        retryBankNow(id);
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(event(id).getDepositedAt()).isEqualTo(depositedAt);
    }

    @Test
    void pgHistoryFailureRollsBackPgAndRetryDoesNotDepositTwice() throws Exception {
        String id = issue("merchant-a", "PG-HISTORY-ROLLBACK");
        doThrow(new DataIntegrityViolationException("simulated")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.DEPOSIT));
        deposit(id).andExpect(status().isOk());
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isNull();
        assertThat(notifications.count()).isZero();
        reset(histories);
        retryBankNow(id);
        deposit(id).andExpect(status().isOk());
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
    }

    @Test
    void bankEntryAndProcessRestartRetryTheDurableBankDepositOutbox() throws Exception {
        String id = issue("merchant-a", "BANK-DIRECT");
        bank.deposit(request(id));
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        bankDispatcher.retryPending();
        bankDispatcher.retryPending();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void depositCanRecoverAnIssueWhosePgConfirmationWasLost() throws Exception {
        String id = prepare("merchant-a", "ISSUE-NOT-CONFIRMED");
        financial.issueGatewayAccount(new BankVirtualAccountIssueRequest(id, BankCompany.KB, new BigDecimal("100"), deadline));
        deposit(id).andExpect(status().isOk());
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
    }

    @Test
    void notificationFailureAndBadAcknowledgementsNeverTurnTheDepositIntoAFailure() throws Exception {
        String id = issue("merchant-a", "NOTIFY-FAIL");
        deposit(id).andExpect(status().isOk());
        mode.set("ERROR");
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        retryNow(id);
        mode.set("BAD_ACK");
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        retryNow(id);
        mode.set("APPLIED");
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void notificationResponseLossReusesTheExactEventAndFirstDeposit() throws Exception {
        String id = issue("merchant-a", "NOTIFY-RESPONSE-LOST");
        deposit(id).andExpect(status().isOk());
        doAnswer(invocation -> { invocation.callRealMethod(); throw new ResourceAccessException("simulated"); })
                .doCallRealMethod().when(adapter).send(any(), anyString(), any());
        dispatcher.retryPending();
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(received).hasSize(2);
        assertThat(new ArrayList<>(received).get(0)).isEqualTo(new ArrayList<>(received).get(1));
    }

    @Test
    void cancelledReservationConvergesWithoutDeletingTheOriginalDepositOrReturningFunds() throws Exception {
        String id = issue("merchant-a", "CANCELLED-RESERVATION");
        deposit(id).andExpect(status().isOk());
        mode.set("CANCELLED");
        dispatcher.retryPending();
        assertThat(event(id).getOutcome()).isEqualTo("RESERVATION_CANCELLED");
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
        DummyVirtualAccount account = accounts.findByPgPaymentId(id).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(VirtualAccountPaymentStatus.CANCELLED);
        assertThat(account.getDepositedAt()).isEqualTo(depositedAt);
        assertThat(account.getAmount()).isEqualByComparingTo("100");
        deposit(id).andExpect(status().isOk()).andExpect(jsonPath("$.pgStatus").doesNotExist());
        assertThat(bankHistories.count()).isEqualTo(3);
        assertThat(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id, GatewayPaymentOperation.CANCEL,
                "MERCHANT_REJECTED_DEPOSIT").orElseThrow().getDetails()).containsEntry("fundsReturned", false);
    }

    @Test
    void failedCancellationCompletionRollsBackPgAndOutboxAndRetriesTheSameBankCleanup() throws Exception {
        String id = issue("merchant-a", "CANCEL-COMMIT-FAIL");
        deposit(id).andExpect(status().isOk());
        mode.set("CANCELLED");
        doThrow(new DataIntegrityViolationException("simulated")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.CANCEL));
        dispatcher.retryPending();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.CANCELLED);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        reset(histories);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(bankHistories.count()).isEqualTo(3);
    }

    @Test
    void disabledTargetRetainsTheSameFinancialSuccessUntilItCanBeNotified() throws Exception {
        String id = issue("merchant-a", "DISABLED");
        deposit(id).andExpect(status().isOk());
        endpointService.changeActive(first.getMerchantId(), "default", false);
        dispatcher.retryPending();
        assertThat(received).isEmpty();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        endpointService.changeActive(first.getMerchantId(), "default", true);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void expiredLeasesCanBeReclaimedAndLateWorkersCannotOverwriteTheCurrentResult() throws Exception {
        String id = issue("merchant-a", "LEASE");
        deposit(id).andExpect(status().isOk());
        BankNotificationSnapshot old = delivery.claim(event(id).getNotificationId());
        assertThat(delivery.claim(event(id).getNotificationId())).isNull();
        jdbc.update("update gateway_bank_notification_outbox set lease_until = ? where pg_payment_id = ?",
                LocalDateTime.now().minusSeconds(1), id);
        dispatcher.retryPending();
        delivery.failed(old, "late response");
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void depositOrNotificationHttpCannotRunInsideAnOuterTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> bankClient.send(null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> dispatcher.retryPending()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void depositsRequireProviderAuthenticationAndInternalBankEntryIsNotPublic() throws Exception {
        String id = issue("merchant-a", "AUTH");
        byte[] body = json.writeValueAsBytes(request(id));
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/internal/financial-simulations/banks/virtual-accounts/gateway/deposit")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void merchantSuspensionDoesNotDiscardADurableBankDeposit() throws Exception {
        String id = issue("merchant-a", "SUSPENDED-DEPOSIT-RECOVERY");
        bank.deposit(request(id));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                merchants.findById(first.getMerchantId()).orElseThrow().suspend());
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aLateFirstDepositIsRejectedButRecoveryPreservesAnOnTimeExistingDeposit() throws Exception {
        String id = issue("merchant-a", "DEADLINE");
        GatewayVirtualAccountDepositRequest late = request(id);
        late.setDepositedAt(deadline.plusSeconds(1));
        postDeposit(late).andExpect(status().isConflict());
        assertThat(bankHistories.count()).isEqualTo(1);
        bank.deposit(request(id));
        bankDispatcher.retryPending();
        assertThat(event(id).getDepositedAt()).isEqualTo(depositedAt);
    }

    @Test
    void callerRollbackAlsoRollsBackTheBankDepositHistoryAndOutbox() throws Exception {
        String id = issue("merchant-a", "JOIN-CALLER-TRANSACTION");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            bank.deposit(request(id));
            assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
            assertThat(bankHistories.count()).isEqualTo(2);
            assertThat(bankOutboxes.count()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isNull();
        assertThat(bankHistories.count()).isEqualTo(1);
        assertThat(bankOutboxes.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void rejectedDepositCleanupAlsoJoinsTheCallerTransaction() throws Exception {
        String id = issue("merchant-a", "JOIN-CLEANUP-TRANSACTION");
        bank.deposit(request(id));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            bank.rejectDeposit(id);
            assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.CANCELLED);
            status.setRollbackOnly();
        });
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isEqualTo(depositedAt);
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(bankOutboxes.count()).isEqualTo(1);
    }

    @Test
    void callerRollbackAlsoRollsBackPgDepositHistoryAndNotificationWhileTheBankFactRemainsCommitted() throws Exception {
        String id = issue("merchant-a", "JOIN-PG-CALLER-TRANSACTION");
        bank.deposit(request(id));
        BankVirtualAccountDepositResponse bankDeposit = bank.find(id).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            depositCommands.confirm(id, bankDeposit);
            assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
            assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
            assertThat(notifications.count()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isNull();
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        assertThat(notifications.count()).isZero();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("PENDING");
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void bankAcknowledgesDepositBeforePgAndMerchantHaveProcessedIt() throws Exception {
        String id = issue("merchant-a", "ASYNC-BOUNDARIES");
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").header("X-Payment-Provider-Token", "bank-provider")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEPOSITED"))
                .andExpect(jsonPath("$.pgStatus").doesNotExist());
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isNull();
        assertThat(notifications.count()).isZero();
        assertThat(bankOutboxes.count()).isEqualTo(1);
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("PENDING");
        bankDispatcher.retryPending();
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        mode.set("ERROR");
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void bankOutboxStorageFailureRollsBackTheBankDepositAndHistory() throws Exception {
        String id = issue("merchant-a", "BANK-OUTBOX-ROLLBACK");
        doThrow(new DataIntegrityViolationException("simulated")).when(bankOutboxes).saveAndFlush(any());
        deposit(id).andExpect(status().isServiceUnavailable());
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isNull();
        assertThat(bankHistories.count()).isEqualTo(1);
        assertThat(bankOutboxes.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void aLostPgAcknowledgementRetriesTheSameDepositWithoutDuplicatingPgOrMerchantOutbox() throws Exception {
        String id = issue("merchant-a", "PG-ACK-LOSS");
        bank.deposit(request(id));
        doAnswer(i -> { i.callRealMethod(); throw new ResourceAccessException("lost acknowledgement"); })
                .when(bankClient).send(any());
        bankDispatcher.retryPending();
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("PENDING");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        Map<String,Object> original = bankOutboxes.findAll().getFirst().getPayload();
        reset(bankClient);
        retryBankNow(id);
        bankDispatcher.retryPending();
        assertThat(bankOutboxes.findAll().getFirst().getPayload()).isEqualTo(original);
        assertThat(bankOutboxes.findAll().getFirst().getStatus()).isEqualTo("DELIVERED");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void theSameDepositEventWithChangedFactsIsRejectedByPgAndCannotOverwriteTheFirstDeposit() throws Exception {
        String id = issue("merchant-a", "CHANGED-BANK-EVENT");
        bank.deposit(request(id));
        BankDepositEvent original = BankDepositEvent.from(bank.find(id).orElseThrow());
        assertThat(bankClient.send(original).matches(original)).isTrue();
        BankVirtualAccountDepositResponse b = original.deposit();
        BankDepositEvent changed = new BankDepositEvent(original.eventId(), new BankVirtualAccountDepositResponse(id,
                b.bankCompany(), b.bankName(), b.accountNumber(), b.amount(), b.expiresAt(), "other-name", b.depositedAt(), b.status()));
        assertThatThrownBy(() -> bankClient.send(changed)).isInstanceOf(RuntimeException.class);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(event(id).getDepositorName()).isEqualTo("입금자");
    }

    @Test
    void internalDepositReceiverRequiresTheFinancialTokenAndTheFixedEventId() throws Exception {
        String id = issue("merchant-a", "BANK-EVENT-AUTH");
        bank.deposit(request(id));
        BankDepositEvent event = BankDepositEvent.from(bank.find(id).orElseThrow());
        String path = "/internal/financial-simulations/gateway/bank-deposits";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(event)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(event))).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> bankClient.send(new BankDepositEvent("other-event", event.deposit())))
                .isInstanceOf(RuntimeException.class);
        assertThat(notifications.count()).isZero();
    }

    GatewayBankNotification event(String id) { return notifications.findByPaymentPgPaymentId(id).orElseThrow(); }

    void retryBankNow(String id) {
        jdbc.update("update bank_transfer_outbox set next_attempt_at = ? where pg_payment_id = ? and flow = 'BANK_DEPOSIT'",
                LocalDateTime.now().minusSeconds(1), id);
    }

    void retryNow(String id) {
        jdbc.update("update gateway_bank_notification_outbox set next_attempt_at = ? where pg_payment_id = ?",
                LocalDateTime.now().minusSeconds(1), id);
    }

    Merchant merchant(String key) {
        Merchant merchant = Merchant.register("같은 표시 이름", MerchantCredentialKey.hash("ticksy".equals(key) ? "merchant-a" : "merchant-b"), GatewayPaymentNotification.CONTRACT, "default");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    @Test void bankExpirationAndPgCommitAreIndependentAndDoNotNotifyTheMerchant() throws Exception {
        String id = issue("merchant-a", "EXPIRATION");
        assertThat(expirations.expire(id, deadline.plusSeconds(1))).isTrue();
        assertThat(expirations.expire(id, deadline.plusSeconds(2))).isFalse();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(expirationRow(id).getStatus()).isEqualTo("PENDING");
        expirationDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(expirationRow(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id,
                GatewayPaymentOperation.EXPIRE, "INITIAL")).isPresent();
        assertThat(notifications.count()).isZero(); assertThat(received).isEmpty();
        mvc.perform(get("/api/v1/payments/gateway/" + id).header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test void aBankDepositBeforeDeadlineCannotExpireEvenWhilePgAndMerchantNotificationsAreDelayed() throws Exception {
        String id = issue("merchant-a", "DEPOSIT-BEFORE-EXPIRY");
        bank.deposit(request(id));
        assertThat(expirations.expire(id, deadline.plusDays(1))).isFalse();
        assertThat(bankOutboxes.findByFlowAndPgPaymentId(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_EXPIRATION, id)).isEmpty();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
        assertThat(expirations.expire(id, deadline.plusDays(2))).isFalse();
        assertThat(received).isEmpty();
        dispatcher.retryPending(); assertThat(received).hasSize(1);
        assertThat(received.peek().depositedAt()).isEqualTo(depositedAt);
    }

    @Test void exactDeadlineIsStillAvailableAndOnlyStrictlyLaterBankTimeCanExpireIt() throws Exception {
        String id = issue("merchant-a", "EXACT-DEADLINE");
        assertThat(expirations.expire(id, deadline)).isFalse();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(expirations.expire(id, deadline.plusSeconds(1))).isTrue();
    }

    @Test void bankExpirationOutboxFailureRollsBackTheAccountAndBankHistory() throws Exception {
        String id = issue("merchant-a", "BANK-EXPIRE-ROLLBACK");
        long before = bankHistories.count();
        doThrow(new DataIntegrityViolationException("expiration outbox failure")).when(bankOutboxes).saveAndFlush(any());
        assertThatThrownBy(() -> expirations.expire(id, deadline.plusSeconds(1))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(bankHistories.count()).isEqualTo(before);
        assertThat(bankOutboxes.count()).isZero();
    }

    @Test void pgExpirationHistoryFailureRollsBackPgAndBankDeliveryRetriesTheSameEvent() throws Exception {
        String id = issue("merchant-a", "PG-EXPIRE-ROLLBACK");
        expirations.expire(id, deadline.plusSeconds(1));
        doThrow(new DataIntegrityViolationException("PG expiration history failure")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.EXPIRE));
        expirationDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(expirationRow(id).getStatus()).isEqualTo("PENDING");
        reset(histories);
        retryExpirationNow(id); expirationDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(expirationRow(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
    }

    @Test void aLostPgAcknowledgementReplaysTheFirstExpirationWithoutAnotherHistory() throws Exception {
        String id = issue("merchant-a", "EXPIRE-LOST-ACK");
        expirations.expire(id, deadline.plusSeconds(1));
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Object response = invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) throw new ResourceAccessException("PG committed; response lost");
            return response;
        }).when(expirationClient).send(any());
        expirationDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(expirationRow(id).getStatus()).isEqualTo("PENDING");
        retryExpirationNow(id); expirationDispatcher.retryPending();
        assertThat(expirationRow(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        BankExpirationEvent original = json.convertValue(expirationRow(id).getPayload(), BankExpirationEvent.class);
        BankExpirationEvent changed = new BankExpirationEvent(original.eventId(), id, original.accountNumber(),
                original.bankCompany(), original.amount(), original.expiresAt(), original.expiredAt().plusSeconds(1));
        assertThatThrownBy(() -> expirationCommands.confirm(changed)).isInstanceOf(
                dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
    }

    @Test void differentMerchantsWithTheSameNumberCannotReadOrExpireEachOthersPgTransactions() throws Exception {
        String a = issue("merchant-a", "SAME-EXPIRE-NO");
        String b = issue("merchant-b", "SAME-EXPIRE-NO");
        expirations.expire(a, deadline.plusSeconds(1)); expirationDispatcher.retryPending();
        assertThat(payments.findById(a).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(payments.findById(b).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        mvc.perform(get("/api/v1/payments/gateway/" + a).header("Merchant", second.getMerchantId()).header("credentialKey", "merchant-b"))
                .andExpect(status().isNotFound());
    }

    @Test void unsignedOrWrongOriginalExpirationCannotChangeAPgPayment() throws Exception {
        String id = issue("merchant-a", "EXPIRE-VALIDATION");
        BankExpirationEvent wrong = new BankExpirationEvent(id + ":EXPIRE:INITIAL", id, request(id).getAccountNumber(),
                BankCompany.KB, new BigDecimal("101"), deadline, deadline.plusSeconds(1));
        mvc.perform(post("/internal/financial-simulations/gateway/bank-expirations")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(wrong)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/financial-simulations/gateway/bank-expirations")
                .header(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, financialToken.value())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(wrong)))
                .andExpect(status().isConflict());
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        bank.deposit(request(id)); bankDispatcher.retryPending();
        BankExpirationEvent impossible = new BankExpirationEvent(id + ":EXPIRE:INITIAL", id, request(id).getAccountNumber(),
                BankCompany.KB, new BigDecimal("100"), deadline, deadline.plusSeconds(1));
        assertThatThrownBy(() -> expirationCommands.confirm(impossible))
                .isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void bankExpirationWaitsForAnEarlierDepositCommitOrRollback(boolean rollback) throws Exception {
        String id = issue("merchant-a", "EXPIRY-RACE");
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                java.util.concurrent.Future<?> deposit = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    accounts.findLinkedByPgPaymentIdForUpdate(id).orElseThrow();
                    bank.deposit(request(id)); held.countDown();
                    try { assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    if (rollback) tx.setRollbackOnly();
                }));
                assertThat(held.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                java.util.concurrent.Future<Boolean> expired = workers.submit(() -> expirations.expire(id, deadline.plusSeconds(1)));
                assertThatThrownBy(() -> expired.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown(); deposit.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(expired.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(rollback);
            } finally { release.countDown(); }
        }
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus())
                .isEqualTo(rollback ? VirtualAccountPaymentStatus.EXPIRED : VirtualAccountPaymentStatus.DEPOSITED);
    }

    @Test void theBankSchedulerAlsoSelectsLinkedAccountsButNeverPaidAccounts() throws Exception {
        String expired = issue("merchant-a", "SCHEDULER-EXPIRE");
        String paid = issue("merchant-a", "SCHEDULER-PAID");
        bank.deposit(request(paid));
        LocalDateTime past = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusMinutes(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("update dummy_virtual_accounts set expires_at = ?", past);
        jdbc.update("update gateway_payments set expires_at = ?", past);
        expirationScheduler.expireWaitingAccounts(); expirationDispatcher.retryPending();
        assertThat(accounts.findByPgPaymentId(expired).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        assertThat(payments.findById(expired).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(accounts.findByPgPaymentId(paid).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
    }

    dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox expirationRow(String id) {
        return bankOutboxes.findByFlowAndPgPaymentId(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_EXPIRATION, id).orElseThrow();
    }

    void retryExpirationNow(String id) {
        jdbc.update("update bank_transfer_outbox set next_attempt_at = ? where flow = 'BANK_EXPIRATION' and pg_payment_id = ?",
                LocalDateTime.now().minusMinutes(1), id);
    }

    String issue(String token, String number) throws Exception {
        String id = prepare(token, number);
        mvc.perform(post("/api/v1/payments/virtual-account/gateway/" + id + "/issue").header("Merchant", "merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                        new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("100"), BankCompany.KB))))
                .andExpect(status().isOk());
        return id;
    }

    String prepare(String token, String number) throws Exception {
        GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(null, number, "buyer", PaymentMethod.BANK_TRANSFER,
                new BigDecimal("100"), deadline, BankCompany.KB);
        return json.readTree(mvc.perform(post("/api/v1/payments/gateway/prepare").header("Merchant", "merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("pgPaymentId").asText();
    }

    GatewayVirtualAccountDepositRequest request(String id) {
        return new GatewayVirtualAccountDepositRequest(accounts.findByPgPaymentId(id).orElseThrow().getAccountNumber(),
                "입금자", new BigDecimal("100"), depositedAt);
    }

    ResultActions deposit(String id) throws Exception { return postDeposit(request(id)); }

    ResultActions postDeposit(GatewayVirtualAccountDepositRequest request) throws Exception {
        ResultActions response = mvc.perform(post("/api/v1/banks/virtual-accounts/deposit")
                .header("X-Payment-Provider-Token", "bank-provider")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)));
        bankDispatcher.retryPending();
        return response;
    }
}
