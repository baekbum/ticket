package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.config.*;
import dev.bum.payment_gateway_service.controller.advice.*;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayPaymentController;
import dev.bum.payment_gateway_service.controller.financial.BankController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssueCommandService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.GatewayVirtualAccountService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountExpirationScheduler;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import dev.bum.payment_gateway_service.service.gateway.payment.GatewayPaymentCommandService;
import dev.bum.payment_gateway_service.service.gateway.payment.GatewayPaymentService;
import dev.bum.payment_gateway_service.service.gateway.refund.GatewayRefundProcessService;
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
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayVirtualAccountIssuanceIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-account-issue;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.ticket.service-token=test-merchant-a", "app.payment-provider.token=test-provider"
})
@AutoConfigureMockMvc
class GatewayVirtualAccountIssuanceIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    @Import({
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancellationService.class,
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancelCommands.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountCancellationService.class,
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountApiService.class,
            SecurityConfig.class, InternalServiceTokenValidator.class, PaymentProviderTokenValidator.class,
            MerchantAuthenticationService.class, FinancialSimulationHttpConfig.class, FinancialSimulationGatewayService.class, GatewayPaymentService.class,
            GatewayPaymentCommandService.class, GatewayVirtualAccountIssuanceService.class,
            GatewayVirtualAccountIssueCommandService.class, BankVirtualAccountIssuanceService.class,
            BankVirtualAccountIssueCommandService.class, GatewayPaymentController.class,
            GatewayVirtualAccountController.class, BankController.class, dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountRefundService.class, GlobalExceptionHandler.class,
            FinancialSimulationExceptionHandler.class, GatewayVirtualAccountService.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountDepositService.class,
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositCommands.class,
            VirtualAccountCancellationService.class, GatewayVirtualAccountDepositOutboxService.class,
            GatewayVirtualAccountDepositOutboxDispatcher.class, GatewayVirtualAccountDepositOutboxPublisher.class,
            VirtualAccountExpirationScheduler.class})
    static class Config { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MerchantJpaRepository merchants;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired VirtualAccountDepositOutboxJpaRepository depositOutbox;
    @Autowired GatewayBankNotificationJpaRepository bankNotifications;
    @Autowired dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutboxJpaRepository bankOutboxes;
    @Autowired VirtualAccountOutboxEventJpaRepository expiryOutbox;
    @Autowired GatewayVirtualAccountIssuanceService issuance;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VirtualAccountExpirationScheduler expiration;
    @Autowired FinancialSimulationToken internalToken;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @MockitoBean GatewayRefundProcessService refunds;
    @MockitoBean TicketPaymentClient ticket;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    Merchant first;
    Merchant second;
    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 23, 59, 59);

    @BeforeEach
    void setup() {
        bankOutboxes.deleteAll();
        bankNotifications.deleteAll();
        depositOutbox.deleteAll();
        expiryOutbox.deleteAll();
        bankHistories.deleteAll();
        accounts.deleteAll();
        histories.deleteAll();
        payments.deleteAll();
        merchants.deleteAll();
        first = register("ticksy");
        second = register("melon");
    }

    @Test
    void merchantIssueCallsTheBankAndCommitsWaitingStateAndTheIssueHistory() throws Exception {
        String id = prepare("test-merchant-a", "PAY-ISSUE", PaymentMethod.BANK_TRANSFER);
        JsonNode result = issued("test-merchant-a", id);
        assertThat(result.get("pgStatus").asText()).isEqualTo("WAITING_DEPOSIT");
        assertThat(result.get("bankStatus").asText()).isEqualTo("WAITING_DEPOSIT");
        assertThat(result.get("paymentNo").asText()).isEqualTo("PAY-ISSUE");
        DummyVirtualAccount account = accounts.findByPgPaymentId(id).orElseThrow();
        assertThat(account.getPaymentNo()).isEqualTo(id);
        assertThat(account.getExpiresAt()).isEqualTo(deadline);
        assertThat(account.getAccountNumber()).isEqualTo(result.get("accountNumber").asText());
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        assertThat(bankHistories.count()).isEqualTo(1);
        assertThat(depositOutbox.count()).isZero();
        verifyNoInteractions(ticket);
    }

    @Test
    void completedIssueReplaysTheFirstAccountWithoutCallingTheBankAgain() throws Exception {
        String id = prepare("test-merchant-a", "PAY-REPLAY", PaymentMethod.BANK_TRANSFER);
        JsonNode firstResult = issued("test-merchant-a", id);
        doThrow(new ResourceAccessException("unavailable")).when(financial).gatewayAccountStatus(anyString());
        JsonNode replay = json.readTree(issue("test-merchant-a", id.toUpperCase(Locale.ROOT),
                new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("100.0000"), BankCompany.KB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(replay).isEqualTo(firstResult);
        verify(financial, times(1)).gatewayAccountStatus(id);
        verify(financial, times(1)).issueGatewayAccount(any());
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
    }

    @Test
    void twoMerchantsCanIssueAccountsForTheSameMerchantPaymentNumber() throws Exception {
        String a = prepare("test-merchant-a", "SAME-NO", PaymentMethod.BANK_TRANSFER);
        String b = prepare("test-merchant-b", "SAME-NO", PaymentMethod.BANK_TRANSFER);
        JsonNode issuedA = issued("test-merchant-a", a);
        JsonNode issuedB = issued("test-merchant-b", b);
        assertThat(a).isNotEqualTo(b);
        assertThat(issuedA.get("accountNumber")).isNotEqualTo(issuedB.get("accountNumber"));
        assertThat(accounts.count()).isEqualTo(2);
        assertThat(accounts.findByPgPaymentId(a).orElseThrow().getPaymentNo()).isEqualTo(a);
        assertThat(accounts.findByPgPaymentId(b).orElseThrow().getPaymentNo()).isEqualTo(b);
    }

    @Test
    void authenticationAndOwnershipAreCheckedBeforeAnyFinancialCall() throws Exception {
        String id = prepare("test-merchant-a", "PAY-ACCESS", PaymentMethod.BANK_TRANSFER);
        issue("test-merchant-b", id, request()).andExpect(status().isNotFound());
        issue("test-merchant-a", UUID.randomUUID().toString(), request()).andExpect(status().isNotFound());
        issue("test-merchant-a", id, new GatewayVirtualAccountPaymentIssueRequest(second.getMerchantId(), "buyer",
                new BigDecimal("100"), BankCompany.KB)).andExpect(status().isForbidden());
        issue("invalid", id, request()).andExpect(status().isUnauthorized());
        mvc.perform(post(path(id)).header("X-Payment-Provider-Token", "test-provider")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request())))
                .andExpect(status().isUnauthorized());
        verify(financial, never()).gatewayAccountStatus(anyString());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void changedAmountBuyerBankOrStoredCardMethodCannotExecuteAnAccountIssue() throws Exception {
        String id = prepare("test-merchant-a", "PAY-TERMS", PaymentMethod.BANK_TRANSFER);
        for (GatewayVirtualAccountPaymentIssueRequest changed : List.of(
                new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("200"), BankCompany.KB),
                new GatewayVirtualAccountPaymentIssueRequest(null, "other", new BigDecimal("100"), BankCompany.KB),
                new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("100"), BankCompany.SHINHAN))) {
            issue("test-merchant-a", id, changed).andExpect(status().isConflict());
        }
        String card = prepare("test-merchant-a", "PAY-CARD", PaymentMethod.CREDIT_CARD);
        issue("test-merchant-a", card, request()).andExpect(status().isConflict());
        issue("test-merchant-a", id, new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", BigDecimal.ZERO,
                BankCompany.KB)).andExpect(status().isBadRequest());
        issue("test-merchant-a", "bad-id", request()).andExpect(status().isBadRequest());
        verify(financial, never()).issueGatewayAccount(any());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void suspensionRejectsNewExecutionButAllowsReplayingAnAlreadyCompletedIssue() throws Exception {
        String issuedId = prepare("test-merchant-a", "PAY-DONE", PaymentMethod.BANK_TRANSFER);
        String readyId = prepare("test-merchant-a", "PAY-READY", PaymentMethod.BANK_TRANSFER);
        JsonNode previous = issued("test-merchant-a", issuedId);
        new TransactionTemplate(transactions).executeWithoutResult(status -> merchants.findById(first.getMerchantId())
                .orElseThrow().suspend());
        issue("test-merchant-a", readyId, request()).andExpect(status().isForbidden());
        assertThat(issued("test-merchant-a", issuedId)).isEqualTo(previous);
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void theSameIssueKeyRejectsChangedAmountsAtBothGatewayAndBankBoundaries() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CHANGED-RETRY", PaymentMethod.BANK_TRANSFER);
        issued("test-merchant-a", id);
        issue("test-merchant-a", id, new GatewayVirtualAccountPaymentIssueRequest(null, "buyer",
                new BigDecimal("200"), BankCompany.KB)).andExpect(status().isConflict());
        BankVirtualAccountIssueRequest changed = new BankVirtualAccountIssueRequest(id, BankCompany.KB,
                new BigDecimal("200"), deadline);
        mvc.perform(post("/internal/financial-simulations/banks/virtual-accounts/gateway/issue")
                .header(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, internalToken.value())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(changed)))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getAmount()).isEqualByComparingTo("100");
        assertThat(bankHistories.count()).isEqualTo(1);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
    }

    @Test
    void anExpiredUnissuedPaymentCannotCreateAnAccount() throws Exception {
        String id = historicalReady(false);
        issue("test-merchant-a", id, request()).andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
    }

    @Test
    void anExistingBankIssueCanBeRecoveredAfterItsDeadlineAndIsExcludedFromLegacyExpiry() throws Exception {
        String id = historicalReady(true);
        issue("test-merchant-a", id, request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1111-9999-999999"));
        expiration.expireWaitingAccounts();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(expiryOutbox.count()).isZero();
    }

    @Test
    void concurrentRequestsConvergeToOneBankAccountAndOnePgIssueHistory() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CONCURRENT", PaymentMethod.BANK_TRANSFER);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int count = 0; count < 8; count++) {
                futures.add(pool.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return issued("test-merchant-a", id).get("accountNumber").asText();
                }));
            }
            start.countDown();
            Set<String> numbers = new HashSet<>();
            for (Future<String> result : futures) numbers.add(result.get(30, TimeUnit.SECONDS));
            assertThat(numbers).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(bankHistories.count()).isEqualTo(1);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
    }

    @Test
    void bankHistoryStorageFailureRollsBackTheAccountAndLeavesPgReady() throws Exception {
        String id = prepare("test-merchant-a", "PAY-BANK-FAIL", PaymentMethod.BANK_TRANSFER);
        doThrow(new DataIntegrityViolationException("simulated bank history storage failure"))
                .when(bankHistories).saveAndFlush(any());
        issue("test-merchant-a", id, request()).andExpect(status().isServiceUnavailable());
        assertThat(accounts.count()).isZero();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(1);
    }

    @Test
    void pgHistoryFailureRollsBackPgStateAndRetryRecoversTheCommittedBankAccount() throws Exception {
        String id = prepare("test-merchant-a", "PAY-PG-FAIL", PaymentMethod.BANK_TRANSFER);
        doThrow(new DataIntegrityViolationException("simulated PG history storage failure")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.ISSUE_ACCOUNT));
        issue("test-merchant-a", id, request()).andExpect(status().isServiceUnavailable());
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(1);
        reset(histories);
        issued("test-merchant-a", id);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        verify(financial, times(1)).issueGatewayAccount(any());
    }

    @Test
    void aLostBankResponseIsRecoveredByQueryWithoutIssuingAgain() throws Exception {
        String id = prepare("test-merchant-a", "PAY-LOST", PaymentMethod.BANK_TRANSFER);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ResourceAccessException("simulated response loss after bank commit");
        }).when(financial).issueGatewayAccount(any());
        issue("test-merchant-a", id, request()).andExpect(status().isServiceUnavailable());
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        issued("test-merchant-a", id);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        verify(financial, times(1)).issueGatewayAccount(any());
    }

    @Test
    void aMismatchedBankResultCannotBeRecordedAsPgSuccess() throws Exception {
        String id = prepare("test-merchant-a", "PAY-BAD-REPLY", PaymentMethod.BANK_TRANSFER);
        doReturn(Optional.of(new BankVirtualAccountIssueResponse(id, BankCompany.KB, BankCompany.KB.getBankName(),
                "1111-9999-999999", new BigDecimal("999"), deadline, VirtualAccountPaymentStatus.WAITING_DEPOSIT)))
                .when(financial).gatewayAccountStatus(id);
        issue("test-merchant-a", id, request()).andExpect(status().isServiceUnavailable());
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(1);
    }

    @Test
    void legacyReadAndCancelStayIsolatedWhileProviderDepositsUseTheLinkedPgFlow() throws Exception {
        String id = prepare("test-merchant-b", "PAY-ISOLATED", PaymentMethod.BANK_TRANSFER);
        JsonNode issued = issued("test-merchant-b", id);
        mvc.perform(get("/api/v1/payments/virtual-account/internal/" + id + "/status")
                .header("X-Service-Token", "test-merchant-a")).andExpect(status().isBadRequest());
        GatewayVirtualAccountCancelRequest cancel = new GatewayVirtualAccountCancelRequest(id,
                issued.get("bankName").asText(), issued.get("accountNumber").asText(), new BigDecimal("100"));
        mvc.perform(post("/api/v1/payments/virtual-account/cancel").header("X-Service-Token", "test-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cancel)))
                .andExpect(status().isBadRequest());
        GatewayVirtualAccountDepositRequest deposit = new GatewayVirtualAccountDepositRequest(
                issued.get("accountNumber").asText(), "입금자", new BigDecimal("100"), LocalDateTime.now());
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").header("X-Payment-Provider-Token", "test-provider")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(deposit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pgPaymentId").value(id));
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(depositOutbox.count()).isZero();
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(bankNotifications.count()).isZero();
        assertThat(bankOutboxes.count()).isEqualTo(1);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        verifyNoInteractions(ticket);
    }

    @Test
    void oldInternalDepositAliasesAreRemovedEvenForTheFinancialClient() throws Exception {
        for (String suffix : new String[]{"/deposit", "/gateway/deposit"}) {
            mvc.perform(post("/internal/financial-simulations/banks/virtual-accounts" + suffix)
                    .header(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, internalToken.value())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Test
    void thePgCoordinatorRejectsCallsInsideAnOpenDatabaseTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> issuance.issue(null,
                UUID.randomUUID().toString(), request()))).isInstanceOf(IllegalTransactionStateException.class);
    }

    String prepare(String token, String paymentNo, PaymentMethod method) throws Exception {
        GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(null, paymentNo, "buyer", method,
                new BigDecimal("100"), deadline, method == PaymentMethod.BANK_TRANSFER ? BankCompany.KB : null);
        return json.readTree(mvc.perform(post("/api/v1/payments/gateway/prepare").header("Merchant", "test-merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("pgPaymentId").asText();
    }

    JsonNode issued(String token, String id) throws Exception {
        return json.readTree(issue(token, id, request()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    ResultActions issue(String token, String id, GatewayVirtualAccountPaymentIssueRequest request) throws Exception {
        return mvc.perform(post(path(id)).header("Merchant", "test-merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)));
    }

    String path(String id) {
        return "/api/v1/payments/virtual-account/gateway/" + id + "/issue";
    }

    GatewayVirtualAccountPaymentIssueRequest request() {
        return new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("100"), BankCompany.KB);
    }

    Merchant register(String key) {
        Merchant merchant = Merchant.register("동일한 가맹점명", MerchantCredentialKey.hash("ticksy".equals(key) ? "test-merchant-a" : "test-merchant-b"), "TICKSY", key);
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    String historicalReady(boolean bankIssued) {
        return new TransactionTemplate(transactions).execute(status -> {
            GatewayPaymentTerms terms = new GatewayPaymentTerms("PAY-HISTORICAL", "buyer", PaymentMethod.BANK_TRANSFER,
                    new BigDecimal("100"), LocalDateTime.now().minusDays(1).withNano(0), BankCompany.KB);
            GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(first, terms));
            histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
            if (bankIssued) {
                DummyVirtualAccount account = accounts.saveAndFlush(DummyVirtualAccount.issue(payment.getPgPaymentId(),
                        payment.getPgPaymentId(), BankCompany.KB, "1111-9999-999999", terms.amount(), terms.expiresAt()));
                bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.issued(account));
            }
            return payment.getPgPaymentId();
        });
    }
}
