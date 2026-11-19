package dev.bum.payment_gateway_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.payment_gateway_service.controller.financial.BankController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayCardPaymentController;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApprovalService;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={GatewayCardPaymentController.class,GatewayVirtualAccountController.class,BankController.class})
@Import(SecurityConfig.class)
class SecurityConfigAuthorizationTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    @MockitoBean PaymentProviderTokenValidator providers;
    @MockitoBean MerchantAuthenticationService merchants;
    @MockitoBean GatewayCardApprovalService cardApprovals;
    @MockitoBean GatewayVirtualAccountIssuanceService issuance;
    @MockitoBean GatewayVirtualAccountCancellationService cancellation;
    @MockitoBean BankVirtualAccountIssuanceService bankIssuance;
    @MockitoBean BankVirtualAccountIssueCommandService bankAccounts;
    @MockitoBean BankVirtualAccountDepositService deposits;
    @MockitoBean BankVirtualAccountCancellationService bankCancellation;
    @MockitoBean BankVirtualAccountRefundService bankRefunds;

    @Test void oldServiceTokenCannotReachRetiredPostRoutes() throws Exception {
        for(String path:new String[]{"/api/v1/payments/card/approve","/api/v1/payments/card/refund",
                "/api/v1/payments/virtual-account/issue","/api/v1/payments/virtual-account/cancel",
                "/api/v1/payments/virtual-account/refund","/api/v1/payments/refunds/internal/resolve"}) {
            mvc.perform(post(path).header("X-Service-Token","previous-service-token")).andExpect(status().isForbidden());
        }
    }

    @Test void usersAndAdminsCannotReachRetiredCardRoutes() throws Exception {
        for(String role:new String[]{"USER","ADMIN"}) {
            mvc.perform(post("/api/v1/payments/card/approve").with(user("caller").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/payments/card/PAY-OLD").with(user("caller").roles(role))).andExpect(status().isForbidden());
        }
    }

    @Test void oldServiceTokenCannotReadPaymentNumberOnlyResults() throws Exception {
        for(String path:new String[]{"/api/v1/payments/card/internal/PAY-OLD/approval-exists",
                "/api/v1/payments/virtual-account/internal/PAY-OLD/status"}) {
            mvc.perform(get(path).header("X-Service-Token","previous-service-token")).andExpect(status().isForbidden());
        }
    }

    @Test void paymentProviderCanReachTheBankDepositEntry() throws Exception {
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit")
                .header(PaymentProviderAuthenticationFilter.PROVIDER_TOKEN_HEADER,"provider-token"))
                .andExpect(status().isBadRequest());
    }

    @Test void usersAndOldServiceTokensCannotDeposit() throws Exception {
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").with(user("buyer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").header("X-Service-Token","previous-service-token")).andExpect(status().isForbidden());
    }

    @Test void providerCannotUseRetiredDepositEntries() throws Exception {
        for(String path:new String[]{"/api/v1/banks/virtual-accounts/legacy/deposit",
                "/api/v1/payments/virtual-account/deposit","/api/v1/support/banks/virtual-accounts/deposit"}) {
            mvc.perform(post(path).header(PaymentProviderAuthenticationFilter.PROVIDER_TOKEN_HEADER,"provider-token")).andExpect(status().isForbidden());
        }
    }

    @Test void userJwtCannotReplaceMerchantCredentials() throws Exception {
        mvc.perform(get("/api/v1/payments/card/gateway/PG-ID").with(user("buyer").roles("USER"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/virtual-account/gateway/PG-ID/issue").with(user("buyer").roles("USER"))).andExpect(status().isUnauthorized());
    }
}
