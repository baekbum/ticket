package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayPaymentMethodController;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.card.GatewayCardSessionToken;
import dev.bum.payment_gateway_service.security.merchant.*;
import dev.bum.payment_gateway_service.service.financial.card.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.notification.MerchantNotificationTargetResolver;
import dev.bum.payment_gateway_service.service.gateway.payment.*;
import dev.bum.payment_gateway_service.support.CardCredentials;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayPaymentMethodChangeIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false"})
@AutoConfigureMockMvc
class GatewayPaymentMethodChangeIntegrationTest {

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("CARD_METHOD_TEST_POSTGRES_URL");
        if (url != null && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/card_method_test")) throw new IllegalArgumentException("격리된 전환 테스트 DB만 사용합니다.");
        properties.add("spring.datasource.url", () -> url == null ? "jdbc:h2:mem:pg-card-method;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : url);
        properties.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> url == null ? "sa" : "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    static class Faults {
        AtomicInteger lost = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch closeStarted = new CountDownLatch(1);
    }

    @Configuration
    @Import({GatewayCardApprovalIntegrationTest.Config.class, GatewayPaymentMethodController.class,
            GatewayPaymentMethodChangeService.class, GatewayPaymentMethodChangeCommands.class})
    static class Config {
        @Bean Faults faults() { return new Faults(); }
        @Bean @Primary RestClient gateTestClient(FinancialSimulationToken token, Faults faults) {
            return RestClient.builder().defaultHeader(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, token.value())
                    .requestInterceptor((request, body, next) -> {
                        boolean close = request.getURI().getPath().endsWith("/close-approval");
                        if (close) { faults.calls.incrementAndGet(); faults.closeStarted.countDown(); }
                        org.springframework.http.client.ClientHttpResponse response = next.execute(request, body);
                        if (close && faults.lost.getAndUpdate(n -> Math.max(0,n-1)) > 0) {
                            response.close(); throw new ResourceAccessException("simulated closed gate response loss");
                        }
                        return response;
                    }).build();
        }
    }

    @MockitoBean dev.bum.payment_gateway_service.service.gateway.payment.GatewayBankMethodChangeService bankChanges;
    @Autowired GatewayPaymentMethodChangeService changes;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired MerchantJpaRepository merchants;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository approvals;
    @Autowired CardApprovalGateJpaRepository gates;
    @Autowired GatewayCardNotificationJpaRepository notifications;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @MockitoSpyBean PasswordEncoder encoder;
    @Autowired CardApprovalService cardApprovals;
    @Autowired GatewayCardApprovalService pgApprovals;
    @Autowired GatewayCardCheckoutService checkout;
    @Autowired GatewayCardSessionToken tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Faults faults;
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore jwtStates;
    @MockitoBean MerchantNotificationTargetResolver destinations;
    String id;
    Long merchantId;
    Long cardId;
    final LocalDateTime cardDeadline = LocalDateTime.of(2099,1,1,12,0);
    final LocalDateTime bankDeadline = LocalDateTime.of(2099,1,2,23,59,59);

    @BeforeEach void setup() {
        reset(histories, financial, encoder);
        faults.lost.set(0); faults.calls.set(0); faults.closeStarted = new CountDownLatch(1);
        notifications.deleteAll(); histories.deleteAll(); gates.deleteAll(); approvals.deleteAll(); payments.deleteAll(); cards.deleteAll(); merchants.deleteAll();
        Merchant merchant = Merchant.register("ticksy", MerchantCredentialKey.hash("ticksy_switch_test"), "CARD_NOTIFICATION_V1", "default");
        merchant.activate(); merchantId = merchants.saveAndFlush(merchant).getMerchantId();
        cardId = cards.saveAndFlush(DummyCard.builder().userId("owner").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash(CardCredentials.hash("4111111111111111")).cardNumberLast4("1111")
                .cvcHash(encoder.encode("123")).cardPasswordHash(encoder.encode("password"))
                .customerName("owner").issuedAt(LocalDate.of(2020,1,1)).expiresAt(LocalDate.of(2099,1,1))
                .limitAmount(new BigDecimal("1000")).build()).getDummyCardId();
        id = payments.saveAndFlush(GatewayPayment.prepare(merchant,new GatewayPaymentTerms("PAY-SWITCH","buyer",
                PaymentMethod.CREDIT_CARD,new BigDecimal("100"),cardDeadline,null))).getPgPaymentId();
    }

    AuthenticatedMerchant principal() { return new AuthenticatedMerchant(merchantId); }
    GatewayCardMethodChangeRequest request() { return new GatewayCardMethodChangeRequest(merchantId,"buyer",new BigDecimal("100"),BankCompany.KB,bankDeadline); }
    FinancialCardApproveRequest cardRequest() { return new FinancialCardApproveRequest(id,"buyer",CardCompany.SHINHAN,"4111111111111111","123","password",new BigDecimal("100"),cardDeadline); }
    GatewayCardApprovalRequest pgCardRequest() { return new GatewayCardApprovalRequest(merchantId,"buyer",CardCompany.SHINHAN,"4111111111111111","123","password",new BigDecimal("100")); }
    FinancialCardApprovalGateRequest closeRequest() { return new FinancialCardApprovalGateRequest(id,"buyer",new BigDecimal("100"),cardDeadline); }
    BigDecimal used() { return cards.findById(cardId).orElseThrow().getCurrentMonthUsedAmount(); }

    @Test void unapprovedCardSwitchesOnceUsingTheSameIdAndInvalidatesTheOldSession() {
        GatewayCardSessionResponse session = new TransactionTemplate(transactions).execute(tx -> tokens.issue(payments.findById(id).orElseThrow(),true));
        GatewayCardMethodChangeResponse changed = changes.change(principal(),id,request());
        assertThat(changed.outcome()).isEqualTo("SWITCHED");
        assertThat(changed.pgPaymentId()).isEqualTo(id);
        assertThat(changed.methodRevision()).isEqualTo(1);
        assertThat(changed.bankCompany()).isEqualTo(BankCompany.KB);
        assertThat(changes.change(principal(),id,request())).isEqualTo(changed);
        assertThat(faults.calls.get()).isEqualTo(1);
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        assertThatThrownBy(() -> cardApprovals.approve(cardRequest())).isInstanceOf(GatewayPaymentConflictException.class);
        assertThatThrownBy(() -> checkout.find("buyer",id,session.paymentSession())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(used()).isZero();
    }

    @Test void approvedCardReturnsItsOriginalSuccessAndTheSameNotificationWithoutSwitching() {
        pgApprovals.approve(principal(),id,pgCardRequest());
        GatewayCardMethodChangeResponse result = changes.change(principal(),id,request());
        assertThat(result.outcome()).isEqualTo("APPROVED");
        assertThat(result.approval().transactionId()).isEqualTo(approvals.findByPgPaymentId(id).orElseThrow().getTransactionId());
        assertThat(result.approval()).isEqualTo(new TransactionTemplate(transactions).execute(tx -> notifications.findByPaymentPgPaymentId(id).orElseThrow().toNotification()));
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(result.methodRevision()).isZero();
        assertThat(faults.calls.get()).isZero();
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test void financialSuccessWithLostPgConfirmationIsRecoveredInsteadOfIssuingAnAccount() {
        cardApprovals.approve(cardRequest());
        GatewayCardMethodChangeResponse result = changes.change(principal(),id,request());
        assertThat(result.outcome()).isEqualTo("APPROVED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test void lostClosureResponseDoesNotAuthorizeSwitchAndRetryRecoversTheSameClosedGate() {
        faults.lost.set(1);
        assertThatThrownBy(() -> changes.change(principal(),id,request())).isInstanceOf(FinancialProviderResponseException.class);
        assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        assertThatThrownBy(() -> cardApprovals.approve(cardRequest())).isInstanceOf(GatewayPaymentConflictException.class);
        assertThat(changes.change(principal(),id,request()).outcome()).isEqualTo("SWITCHED");
        assertThat(used()).isZero();
    }

    @Test void failedPgTransitionStorageRollsBackTheMethodButKeepsFinancialApprovalClosed() {
        doThrow(new DataIntegrityViolationException("simulated switch history failure")).when(histories).saveAndFlush(argThat(history -> history != null && "COMPLETED".equals(history.getOperationKey())));
        assertThatThrownBy(() -> changes.change(principal(),id,request())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.findById(id).orElseThrow().getMethodRevision()).isZero();
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        reset(histories);
        assertThat(changes.change(principal(),id,request()).methodRevision()).isEqualTo(1);
    }

    @Test void changedTargetAndOtherMerchantRequestsAreRejectedWithoutReopeningApproval() {
        changes.change(principal(),id,request());
        assertThatThrownBy(() -> changes.change(principal(),id,new GatewayCardMethodChangeRequest(merchantId,"buyer",new BigDecimal("100"),BankCompany.NH,bankDeadline)))
                .isInstanceOf(GatewayPaymentConflictException.class);
        assertThatThrownBy(() -> changes.change(new AuthenticatedMerchant(merchantId+1),id,new GatewayCardMethodChangeRequest(merchantId+1,"buyer",new BigDecimal("100"),BankCompany.KB,bankDeadline)))
                .isInstanceOf(GatewayPaymentNotFoundException.class);
        assertThat(faults.calls.get()).isEqualTo(1);
    }

    @Test void anAlreadyInspectedApprovalCannotExecuteAfterTheFinancialClosureCommits() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return inv.callRealMethod(); }).when(financial).approveGatewayCard(any());
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> approval=pool.submit(() -> pgApprovals.approve(principal(),id,pgCardRequest()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(changes.change(principal(),id,request()).outcome()).isEqualTo("SWITCHED");
            release.countDown();
            assertThatThrownBy(() -> approval.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(used()).isZero();
            assertThat(approvals.count()).isZero();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void aFinancialApprovalAlreadyHoldingTheGateWinsAndIsRecoveredByTheSwitch() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return inv.callRealMethod(); }).when(encoder).matches(anyString(),anyString());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> approval=pool.submit(() -> pgApprovals.approve(principal(),id,pgCardRequest()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            Future<GatewayCardMethodChangeResponse> change=pool.submit(() -> changes.change(principal(),id,request()));
            assertThat(faults.closeStarted.await(10,TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(change.get(15,TimeUnit.SECONDS).outcome()).isEqualTo("APPROVED");
            approval.get(15,TimeUnit.SECONDS);
            assertThat(used()).isEqualByComparingTo("100");
            assertThat(approvals.count()).isEqualTo(1);
            assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void simultaneousGateCreationCannotReopenAClosedApproval() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            Future<FinancialCardApprovalGateResponse> close=pool.submit(() -> {start.await();return cardApprovals.close(closeRequest());});
            Future<?> approval=pool.submit(() -> {start.await();try{return cardApprovals.approve(cardRequest());}catch(GatewayPaymentConflictException rejected){return null;}});
            start.countDown();
            FinancialCardApprovalGateResponse result=close.get(15,TimeUnit.SECONDS); approval.get(15,TimeUnit.SECONDS);
            assertThat(gates.count()).isEqualTo(1);
            assertThat(used()).isEqualByComparingTo(result.closed()?"0":"100");
            assertThat(approvals.count()).isEqualTo(result.closed()?0:1);
        } finally {pool.shutdownNow();}
    }

    @Test void theTransitionApiRequiresMerchantCredentials() throws Exception {
        String path="/api/v1/payments/gateway/"+id+"/method/bank-transfer";
        String body="{\"buyerReference\":\"buyer\",\"amount\":100,\"bankCompany\":\"KB\",\"expiresAt\":\"2099-01-02T23:59:59\"}";
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Merchant",merchantId).header("credentialKey","ticksy_switch_test")
                .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SWITCHED"));
    }

    @Test void aDefinitiveFinancialApprovalFailureCanSwitchWithoutChargingTheCard() {
        approvals.saveAndFlush(DummyCardPaymentHistory.approvalFailed(id,cards.findById(cardId).orElseThrow(),
                "buyer",id,CardCompany.SHINHAN,"4111-****-****-1111",new BigDecimal("100"),"declined"));
        assertThat(changes.change(principal(),id,request()).outcome()).isEqualTo("SWITCHED");
        assertThat(used()).isZero();
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        assertThatThrownBy(()->cardApprovals.approve(cardRequest())).isInstanceOf(GatewayPaymentConflictException.class);
    }
}
