package dev.bum.payment_gateway_service.service.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.config.FinancialSimulationHttpConfig;
import dev.bum.payment_gateway_service.controller.advice.*;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayCardPaymentController;
import dev.bum.payment_gateway_service.controller.financial.*;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.exception.CardPaymentPendingException;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.service.financial.card.GatewayCardPaymentService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.GatewayVirtualAccountService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import dev.bum.payment_gateway_service.service.gateway.refund.GatewayRefundProcessService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssueCommandService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositOutboxDispatcher;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositOutboxPublisher;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositOutboxService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FinancialSimulationHttpIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:financial-http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.ticket.service-token=test-service-token", "app.payment-provider.token=test-provider-token"
})
@AutoConfigureMockMvc
class FinancialSimulationHttpIntegrationTest {

    @MockitoBean
    dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApprovalService pgCardApprovals;
    @MockitoBean
    dev.bum.payment_gateway_service.service.financial.card.CardApprovalService financialCardApprovals;

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
            dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApiService.class,
            FinancialSimulationHttpConfig.class, FinancialSimulationGatewayService.class,
            GatewayCardPaymentController.class, GatewayVirtualAccountController.class,
            CardController.class, BankController.class, dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountRefundService.class, GlobalExceptionHandler.class,
            FinancialSimulationExceptionHandler.class, GatewayVirtualAccountService.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountDepositService.class,
            dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositCommands.class,
            VirtualAccountCancellationService.class, GatewayVirtualAccountDepositOutboxService.class,
            GatewayVirtualAccountDepositOutboxDispatcher.class, GatewayVirtualAccountDepositOutboxPublisher.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssuanceService.class,
            dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssueCommandService.class,
            GatewayVirtualAccountIssuanceService.class, GatewayVirtualAccountIssueCommandService.class})
    static class Config { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FinancialSimulationGatewayService gateway;
    @Autowired FinancialSimulationToken simulationToken;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository histories;
    @Autowired VirtualAccountOutboxEventJpaRepository expirationOutbox;
    @MockitoSpyBean VirtualAccountDepositOutboxJpaRepository depositOutbox;
    @MockitoSpyBean GatewayVirtualAccountService bank;
    @MockitoBean GatewayCardPaymentService cards;
    @MockitoBean GatewayRefundProcessService refunds;
    @MockitoBean TicketPaymentClient ticket;
    @MockitoBean MerchantAuthenticationService merchants;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;

    @BeforeEach
    void clean() {
        depositOutbox.deleteAll();
        expirationOutbox.deleteAll();
        histories.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void cardApprovalTraversesHttpAndUsesTheAuthenticatedBuyerRatherThanAnInboundHeader() throws Exception {
        when(cards.approve(eq("buyer"), any())).thenAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_FINANCIAL_SIMULATOR");
            return GatewayCardPaymentApproveResponse.builder().paymentNo("PAY-CARD").transactionId("CARD-1")
                    .userId("buyer").approved(true).cardCompany(CardCompany.SHINHAN).maskedCardNumber("4111-****-****-1111")
                    .approvedAmount(new BigDecimal("100")).currentMonthUsedAmount(new BigDecimal("250"))
                    .limitAmount(new BigDecimal("1000")).message("승인 완료").build();
        });

        mvc.perform(post("/api/v1/payments/card/approve").with(user())
                .header(FinancialSimulationAuthenticationFilter.USER_HEADER, "victim")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cardRequest())))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.approved").value(true)).andExpect(jsonPath("$.userId").value("buyer"))
                .andExpect(jsonPath("$.transactionId").value("CARD-1"))
                .andExpect(jsonPath("$.cardCompany").value("SHINHAN"))
                .andExpect(jsonPath("$.maskedCardNumber").value("4111-****-****-1111"))
                .andExpect(jsonPath("$.approvedAmount").value(100))
                .andExpect(jsonPath("$.currentMonthUsedAmount").value(250))
                .andExpect(jsonPath("$.limitAmount").value(1000)).andExpect(jsonPath("$.message").value("승인 완료"));
        verify(cards, times(1)).approve(eq("buyer"), any());
    }

    @Test
    void pendingCardApprovalKeepsThe202AndItsOriginalJson() throws Exception {
        when(cards.approve(anyString(), any())).thenThrow(new CardPaymentPendingException("PAY-CARD", null));
        mvc.perform(post("/api/v1/payments/card/approve").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(cardRequest())))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentNo").value("PAY-CARD"));
        verify(cards, times(1)).approve(eq("buyer"), any());
    }

    @Test
    void financialRejectionKeepsThe400AndDoesNotRetry() throws Exception {
        when(cards.approve(anyString(), any())).thenThrow(new IllegalArgumentException("카드 한도 초과"));
        mvc.perform(post("/api/v1/payments/card/approve").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(cardRequest())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("카드 한도 초과"));
        verify(cards, times(1)).approve(eq("buyer"), any());
    }

    @Test
    void cardStatusPreservesOwnershipRejectionAndApprovalExistenceIsRelayed() throws Exception {
        when(cards.status("buyer", "OTHER")).thenThrow(new AccessDeniedException("다른 사용자"));
        mvc.perform(get("/api/v1/payments/card/OTHER").with(user())).andExpect(status().isForbidden());
        when(cards.hasApprovalHistory("PAY-CARD")).thenReturn(true);
        mvc.perform(get("/api/v1/payments/card/internal/PAY-CARD/approval-exists")
                .header("X-Service-Token", "test-service-token"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
    }

    @Test
    void bankIssueAndReadTraverseHttpAndRepeatedIssueKeepsOneAccount() throws Exception {
        JsonNode first = issue("PAY-BANK");
        JsonNode second = issue("PAY-BANK");
        assertThat(first.get("accountNumber")).isEqualTo(second.get("accountNumber"));
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(histories.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/payments/virtual-account/internal/PAY-BANK/status")
                .header("X-Service-Token", "test-service-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(first.get("accountNumber").asText()));
    }

    @Test
    void repeatedBankIssueWithChangedAmountStillFails() throws Exception {
        issue("PAY-BANK");
        GatewayVirtualAccountIssueRequest changed = issueRequest("PAY-BANK");
        changed.setAmount(new BigDecimal("200"));
        bankPost("issue", changed).andExpect(status().isBadRequest());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void depositHttpCommitsAccountHistoryAndOutboxTogetherAndKeepsTheOriginalDepositOnReplay() throws Exception {
        JsonNode issued = issue("PAY-DEPOSIT");
        GatewayVirtualAccountDepositRequest request = depositRequest(issued);
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = providerPost(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEPOSITED"))
                    .andExpect(jsonPath("$.message").exists()).andReturn();
            JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
            assertThat(body.has("pgPaymentId")).isFalse();
            assertThat(body.has("pgStatus")).isFalse();
            assertThat(body.has("notificationStatus")).isFalse();
        }
        DummyVirtualAccount account = accounts.findByPaymentNo("PAY-DEPOSIT").orElseThrow();
        assertThat(account.getDepositedAt()).isEqualTo(request.getDepositedAt());
        assertThat(histories.count()).isEqualTo(2);
        assertThat(depositOutbox.count()).isEqualTo(1);
        assertThat(depositOutbox.findAll().getFirst().getStatus()).isEqualTo(VirtualAccountDepositOutboxStatus.PENDING);
    }

    @Test
    void outboxStorageFailureOverHttpRollsBackDepositAndFinancialHistory() throws Exception {
        JsonNode issued = issue("PAY-ROLLBACK");
        doThrow(new DataIntegrityViolationException("simulated outbox storage failure"))
                .when(depositOutbox).save(any(VirtualAccountDepositOutbox.class));
        providerPost(depositRequest(issued)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCIAL_STORAGE_UNAVAILABLE"));
        assertThat(accounts.findByPaymentNo("PAY-ROLLBACK").orElseThrow().getStatus())
                .isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(depositOutbox.count()).isZero();
    }

    @Test
    void preDepositCancellationOverHttpRemainsIdempotent() throws Exception {
        JsonNode issued = issue("PAY-CANCEL");
        GatewayVirtualAccountCancelRequest request = new GatewayVirtualAccountCancelRequest("PAY-CANCEL",
                issued.get("bankName").asText(), issued.get("accountNumber").asText(), new BigDecimal("100"));
        for (int attempt = 0; attempt < 2; attempt++) {
            bankPost("cancel", request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        assertThat(histories.count()).isEqualTo(2);
    }

    @Test
    void bothRefundRequestsKeepTheirExistingProcessIdentifierAcrossHttp() throws Exception {
        when(cards.refund(any())).thenReturn(GatewayCardPaymentRefundResponse.builder().paymentNo("PAY-CARD")
                .transactionId("CARD-1").refundedAmount(BigDecimal.TEN)
                .status(dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus.PARTIALLY_REFUNDED)
                .message("카드 환불 완료").build());
        GatewayCardPaymentRefundRequest card = new GatewayCardPaymentRefundRequest(71L, "PAY-CARD", "CARD-1", BigDecimal.TEN);
        mvc.perform(post("/api/v1/payments/card/refund").header("X-Service-Token", "test-service-token")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(card))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value("PAY-CARD"))
                .andExpect(jsonPath("$.transactionId").value("CARD-1"))
                .andExpect(jsonPath("$.refundedAmount").value(10))
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        ArgumentCaptor<GatewayCardPaymentRefundRequest> capturedCard = ArgumentCaptor.forClass(GatewayCardPaymentRefundRequest.class);
        verify(cards).refund(capturedCard.capture());
        assertThat(capturedCard.getValue()).isEqualTo(card);

        doReturn(GatewayVirtualAccountRefundResponse.builder().paymentNo("PAY-BANK").refundBankCompany(BankCompany.KB)
                .refundAccountNumber("refund-account").refundAccountHolder("환불 예금주").refundedAmount(BigDecimal.TEN)
                .message("계좌 환불 완료").build()).when(bank).refund(any());
        GatewayVirtualAccountRefundRequest account = new GatewayVirtualAccountRefundRequest(72L, "PAY-BANK", BankCompany.KB,
                "refund-account", "환불 예금주", BigDecimal.TEN);
        bankPost("refund", account).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value("PAY-BANK"))
                .andExpect(jsonPath("$.refundBankCompany").value("KB"))
                .andExpect(jsonPath("$.refundAccountNumber").value("refund-account"))
                .andExpect(jsonPath("$.refundAccountHolder").value("환불 예금주"))
                .andExpect(jsonPath("$.refundedAmount").value(10));
        ArgumentCaptor<GatewayVirtualAccountRefundRequest> capturedBank = ArgumentCaptor.forClass(GatewayVirtualAccountRefundRequest.class);
        verify(bank).refund(capturedBank.capture());
        assertThat(capturedBank.getValue()).isEqualTo(account);
    }

    @Test
    void internalControllersRejectOrdinaryTokensEvenWhenAUserHeaderIsSupplied() throws Exception {
        String path = "/internal/financial-simulations/cards/payments/PAY-CARD/approval-exists";
        mvc.perform(get(path).with(user()).header("X-PG-User-Id", "victim")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Service-Token", "test-service-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Payment-Provider-Token", "test-provider-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-PG-Simulation-Token", "wrong")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cards);
    }

    @Test
    void aDatabaseTransactionCannotSpanTheFinancialHttpCall() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> gateway.approvalExists("PAY-CARD")))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(cards);
    }

    @Test
    void connectionLossReturns503WithoutClaimingAFinancialFailureOrRetrying() throws Exception {
        Object target = AopTestUtils.getUltimateTargetObject(gateway);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        ReflectionTestUtils.setField(target, "configuredBaseUrl", "http://127.0.0.1:" + port);
        try {
            mvc.perform(post("/api/v1/payments/card/approve").with(user()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(cardRequest()))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("FINANCIAL_PROVIDER_UNAVAILABLE"));
            verifyNoInteractions(cards);
        } finally {
            ReflectionTestUtils.setField(target, "configuredBaseUrl", "");
        }
    }

    JsonNode issue(String paymentNo) throws Exception {
        return json.readTree(bankPost("issue", issueRequest(paymentNo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    ResultActions bankPost(String operation, Object request) throws Exception {
        return mvc.perform(post("/api/v1/payments/virtual-account/" + operation)
                .header("X-Service-Token", "test-service-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)));
    }

    ResultActions providerPost(GatewayVirtualAccountDepositRequest request) throws Exception {
        return mvc.perform(post("/api/v1/banks/virtual-accounts/legacy/deposit")
                .header("X-Payment-Provider-Token", "test-provider-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)));
    }

    GatewayVirtualAccountIssueRequest issueRequest(String paymentNo) {
        return new GatewayVirtualAccountIssueRequest(paymentNo, BankCompany.KB, new BigDecimal("100"),
                LocalDateTime.now().plusDays(3).withNano(0));
    }

    GatewayVirtualAccountDepositRequest depositRequest(JsonNode issued) {
        return new GatewayVirtualAccountDepositRequest(issued.get("accountNumber").asText(), "입금자",
                new BigDecimal("100"), LocalDateTime.now().withNano(0));
    }

    GatewayCardPaymentApproveRequest cardRequest() {
        return new GatewayCardPaymentApproveRequest("PAY-CARD", CardCompany.SHINHAN, "4111111111111111", "123", "1234",
                new BigDecimal("100"));
    }

    RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken("buyer", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
