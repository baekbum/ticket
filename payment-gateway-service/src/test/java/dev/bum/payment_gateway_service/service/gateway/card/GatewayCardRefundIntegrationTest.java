package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.controller.gateway.refund.GatewayPaymentRefundController;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.merchant.*;
import dev.bum.payment_gateway_service.service.financial.card.GatewayCardPaymentService;
import dev.bum.payment_gateway_service.service.financial.card.CardPaymentRefundService;
import dev.bum.payment_gateway_service.service.gateway.refund.*;
import dev.bum.payment_gateway_service.support.CardCredentials;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayCardRefundIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.ticket.service-token=test-merchant-a", "app.payment-provider.token=test-provider"})
@AutoConfigureMockMvc
class GatewayCardRefundIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("CARD_REFUND_TEST_POSTGRES_URL");
        if (url != null && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/card_refund_test")) {
            throw new IllegalArgumentException("격리된 카드 환불 테스트 DB만 사용합니다.");
        }
        properties.add("spring.datasource.url", () -> url == null
                ? "jdbc:h2:mem:gateway-card-refund;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : url);
        properties.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> url == null ? "sa" : "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    static class Faults {
        AtomicInteger lost = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
    }

    @Configuration
    @Import({GatewayCardApprovalIntegrationTest.Config.class, GatewayPaymentRefundController.class,
            GatewayPaymentRefundService.class, GatewayCardRefundService.class, GatewayCardRefundCommands.class,
            GatewayCardRefundClient.class, GatewayBankRefundService.class, GatewayBankRefundCommands.class,
            GatewayBankRefundClient.class})
    static class Config {
        @Bean Faults faults() { return new Faults(); }

        @Bean @Primary RestClient refundClient(FinancialSimulationToken token, Faults faults) {
            return RestClient.builder().defaultHeader(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, token.value())
                    .requestInterceptor((request, body, next) -> {
                        boolean refund = request.getURI().getPath().contains("/cards/gateway/refunds/");
                        if (refund) {
                            faults.calls.incrementAndGet();
                            if (faults.unavailable.get() > 0) {
                                return new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0],
                                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                            }
                            if (faults.denied.get() > 0) {
                                return new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0],
                                        org.springframework.http.HttpStatus.CONFLICT);
                            }
                        }
                        org.springframework.http.client.ClientHttpResponse response = next.execute(request, body);
                        if (refund && faults.lost.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            response.close();
                            throw new ResourceAccessException("simulated committed card refund response loss");
                        }
                        return response;
                    }).build();
        }
    }

    @Autowired GatewayPaymentRefundService service;
    @Autowired CardPaymentRefundService financial;
    @Autowired GatewayCardRefundCommands commands;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired MerchantJpaRepository merchants;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository approvals;
    @Autowired GatewayCardNotificationJpaRepository notifications;
    @Autowired GatewayCardRefundJpaRepository refunds;
    @MockitoSpyBean CardPaymentRefundJpaRepository receipts;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Faults faults;
    @Autowired MockMvc mvc;
    @MockitoBean GatewayCardPaymentService legacyCards;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    Merchant first;
    Merchant second;
    DummyCard ledger;
    String paymentId;

    @BeforeEach
    void setup() {
        reset(receipts, histories);
        faults.lost.set(0); faults.calls.set(0); faults.denied.set(0); faults.unavailable.set(0);
        receipts.deleteAll(); refunds.deleteAll(); notifications.deleteAll(); histories.deleteAll();
        approvals.deleteAll(); payments.deleteAll(); cards.deleteAll(); merchants.deleteAll();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            first = merchant("ticksy_a"); second = merchant("melon_b");
            ledger = cards.saveAndFlush(DummyCard.builder().userId("owner").cardCompany(CardCompany.SHINHAN)
                    .cardNumberHash(CardCredentials.hash("4111111111111111")).cardNumberLast4("1111")
                    .cvcHash("test-cvc-hash").cardPasswordHash("test-password-hash").customerName("owner")
                    .issuedAt(LocalDate.of(2020,1,1)).expiresAt(LocalDate.of(2099,1,1))
                    .limitAmount(new BigDecimal("1000")).build());
            paymentId = payment(first, "SAME-PAYMENT");
        });
    }

    Merchant merchant(String key) {
        Merchant merchant = Merchant.register("same name", MerchantCredentialKey.hash(key), "CARD_NOTIFICATION_V1", "default");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    String payment(Merchant merchant, String number) {
        DummyCard current = cards.findByIdForUpdate(ledger.getDummyCardId()).orElseThrow();
        current.approve(new BigDecimal("100"));
        GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(merchant,
                new GatewayPaymentTerms(number, "buyer", PaymentMethod.CREDIT_CARD, new BigDecimal("100"),
                        LocalDateTime.of(2099,1,1,12,0), null)));
        DummyCardPaymentHistory approval = approvals.saveAndFlush(DummyCardPaymentHistory.approved(payment.getPgPaymentId(),
                current, "buyer", payment.getPgPaymentId(), "CARD-" + UUID.randomUUID(), "4111-****-****-1111", payment.getAmount()));
        FinancialCardApprovalResponse response = FinancialCardApprovalResponse.from(approval);
        payment.approveCard(CardCompany.SHINHAN, response.approvedAt());
        GatewayCardNotification notification = GatewayCardNotification.pending(payment, response);
        notification.delivered("ACCEPTED");
        notifications.saveAndFlush(notification);
        return payment.getPgPaymentId();
    }

    GatewayPaymentRefundRequest request(String amount) {
        return new GatewayPaymentRefundRequest(first.getMerchantId(), "buyer", new BigDecimal(amount), null, null, null);
    }

    PaymentRefundResult call(String key, String amount, boolean withdraw) {
        return service.execute(new AuthenticatedMerchant(first.getMerchantId()), paymentId, key, request(amount), withdraw);
    }

    BigDecimal used() { return cards.findById(ledger.getDummyCardId()).orElseThrow().getCurrentMonthUsedAmount(); }

    @Test void partialThenRemainingFullRefundAndDuplicateRequestsRestoreTheLimitOnce() {
        PaymentRefundResult partial = call("one", "40", false);
        assertThat(call("one", "40.00", false)).isEqualTo(partial);
        assertThat(used()).isEqualByComparingTo("60");
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PARTIALLY_REFUNDED);
        assertThat(call("two", "60", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isZero();
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.REFUNDED);
        assertThat(approvals.findByPgPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo(CardPaymentHistoryStatus.REFUNDED);
        assertThat(receipts.count()).isEqualTo(2);
        assertThat(histories.count()).isEqualTo(2);
    }

    @Test void fullApprovalCancellationUsesTheSameRefundWorkflow() {
        assertThat(call("full", "100", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(call("full", "100", true).outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test void withdrawalBeforeExecutionBlocksDelayedExecutionAndAllowsANewApplication() {
        PaymentRefundResult withdrawn = call("first", "40", true);
        assertThat(withdrawn.outcome()).isEqualTo("WITHDRAWN");
        assertThat(call("first", "40", false)).isEqualTo(withdrawn);
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(call("replacement", "60", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isEqualByComparingTo("40");
    }

    @Test void sameKeyWithDifferentAmountAndOverRefundAreRejected() {
        call("one", "40", false);
        assertThatThrownBy(() -> call("one", "41", false)).isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThatThrownBy(() -> call("two", "61", false)).isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(used()).isEqualByComparingTo("60");
    }

    @Test void anotherMerchantCannotAccessTheTransactionButCanUseTheSamePaymentNumberAndRefundKey() {
        assertThatThrownBy(() -> service.execute(new AuthenticatedMerchant(second.getMerchantId()), paymentId, "one",
                new GatewayPaymentRefundRequest(second.getMerchantId(), "buyer", new BigDecimal("40"), null, null, null), false))
                .isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentNotFoundException.class);
        String other = new TransactionTemplate(transactions).execute(tx -> payment(second, "SAME-PAYMENT"));
        call("one", "40", false);
        assertThat(service.execute(new AuthenticatedMerchant(second.getMerchantId()), other, "one",
                new GatewayPaymentRefundRequest(second.getMerchantId(), "buyer", new BigDecimal("40"), null, null, null), false)
                .outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isEqualByComparingTo("120");
    }

    @Test void cardRefundHasNoCallerSelectedAccountAndCannotStartBeforeApprovalNotificationCompletes() {
        assertThatThrownBy(() -> service.execute(new AuthenticatedMerchant(first.getMerchantId()), paymentId, "one",
                new GatewayPaymentRefundRequest(first.getMerchantId(), "buyer", new BigDecimal("40"), BankCompany.KB, "1234", "owner"), false))
                .isInstanceOf(IllegalArgumentException.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> notifications.findByPaymentPgPaymentId(paymentId).orElseThrow().failed("RETRY"));
        assertThatThrownBy(() -> call("one", "40", false)).isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(faults.calls.get()).isZero();
    }

    @Test void committedResponseLossIsRetriedAtMostFourTimesUsingOneFinancialReceipt() {
        faults.lost.set(3);
        assertThat(call("one", "40", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(used()).isEqualByComparingTo("60");
    }

    @Test void fourLostResponsesPreserveTheApplicationForManualRetryOrWithdrawalRecovery() {
        faults.lost.set(4);
        assertThatThrownBy(() -> call("one", "40", false)).isInstanceOf(dev.bum.payment_gateway_service.exception.FinancialProviderResponseException.class);
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isZero();
        assertThat(refunds.findByPaymentPgPaymentIdAndRefundRequestId(paymentId,"one").orElseThrow().getStatus()).isEqualTo("REQUESTED");
        assertThatThrownBy(() -> call("different", "30", false))
                .isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(call("one", "40", true).outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isEqualByComparingTo("60");
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test void definitiveClientRejectionIsNotRetried() {
        faults.denied.set(1);
        assertThatThrownBy(() -> call("one", "40", false)).isInstanceOf(dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException.class);
        assertThat(faults.calls.get()).isEqualTo(1);
        assertThat(receipts.count()).isZero();
    }

    @Test void unavailableCardProviderStopsAfterFourAttemptsAndLeavesTheOriginalApplication() {
        faults.unavailable.set(1);
        assertThatThrownBy(() -> call("one", "40", false))
                .isInstanceOf(dev.bum.payment_gateway_service.exception.FinancialProviderResponseException.class);
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(receipts.count()).isZero();
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(refunds.findByPaymentPgPaymentIdAndRefundRequestId(paymentId,"one").orElseThrow().getStatus()).isEqualTo("REQUESTED");
        faults.unavailable.set(0);
        assertThat(call("one", "40", false).outcome()).isEqualTo("SUCCEEDED");
    }

    @Test void simultaneousPgRetriesApplyOneFinancialReceiptAndOnePgHistory() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentRefundResult> first = pool.submit(() -> { start.await(); return call("one", "40", false); });
            Future<PaymentRefundResult> second = pool.submit(() -> { start.await(); return call("one", "40", false); });
            start.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
            assertThat(used()).isEqualByComparingTo("60");
            assertThat(receipts.count()).isEqualTo(1);
            assertThat(histories.count()).isEqualTo(1);
            assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isEqualByComparingTo("40");
        } finally { pool.shutdownNow(); }
    }

    @Test void pgHistoryFailureRollsBackPgResultAndRetryRecoversTheFinancialSuccess() {
        doThrow(new DataIntegrityViolationException("simulated PG history failure")).when(histories).saveAndFlush(any());
        assertThatThrownBy(() -> call("one", "40", false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isZero();
        assertThat(receipts.count()).isEqualTo(1);
        reset(histories);
        assertThat(call("one", "40", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(used()).isEqualByComparingTo("60");
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test void financialReceiptFailureRollsBackBothApprovalAndCardBalance() {
        doThrow(new DataIntegrityViolationException("simulated card receipt failure")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> financial.refund(new CardRefundCommand(paymentId,"one",new BigDecimal("40"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(approvals.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test void simultaneousFinancialRefundAndWithdrawalConvergeOnOnePermanentResult() throws Exception {
        CardRefundCommand command = new CardRefundCommand(paymentId,"one",new BigDecimal("40"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentRefundResult> refund = pool.submit(() -> { start.await(); return financial.refund(command); });
            Future<PaymentRefundResult> withdraw = pool.submit(() -> { start.await(); return financial.withdraw(command); });
            start.countDown();
            PaymentRefundResult result = refund.get(15,TimeUnit.SECONDS);
            assertThat(withdraw.get(15,TimeUnit.SECONDS)).isEqualTo(result);
            assertThat(receipts.count()).isEqualTo(1);
            assertThat(used()).isEqualByComparingTo("SUCCEEDED".equals(result.outcome()) ? "60" : "100");
        } finally { pool.shutdownNow(); }
    }

    @Test void publicRefundApiUsesMerchantAuthenticationAndTheStoredCardMethod() throws Exception {
        String body = "{\"buyerReference\":\"buyer\",\"amount\":40.000}";
        String path = "/api/v1/payments/gateway/" + paymentId + "/refunds/one";
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Merchant",first.getMerchantId()).header("credentialKey","ticksy_a")
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SUCCEEDED"));
        mvc.perform(post(path).header("Merchant",first.getMerchantId()).header("credentialKey","ticksy_a")
                .contentType("application/json").content("{\"buyerReference\":\"buyer\",\"amount\":40.00}"))
                .andExpect(status().isOk());
        mvc.perform(post(path).header("Merchant",first.getMerchantId()).header("credentialKey","ticksy_a")
                .contentType("application/json").content("{\"buyerReference\":\"buyer\",\"amount\":40.001}"))
                .andExpect(status().isBadRequest());
        assertThat(receipts.count()).isEqualTo(1);
        mvc.perform(post(path).header("Merchant",second.getMerchantId()).header("credentialKey","melon_b")
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
    }
}
