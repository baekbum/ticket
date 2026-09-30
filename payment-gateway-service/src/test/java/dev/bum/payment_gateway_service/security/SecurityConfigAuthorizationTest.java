package dev.bum.payment_gateway_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.payment_gateway_service.controller.card.GatewayCardPaymentController;
import dev.bum.payment_gateway_service.controller.virtualAccount.GatewayVirtualAccountController;
import dev.bum.payment_gateway_service.service.card.GatewayCardPaymentService;
import dev.bum.payment_gateway_service.service.virtualAccount.GatewayVirtualAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {GatewayCardPaymentController.class, GatewayVirtualAccountController.class},
        properties = "app.ticket.base-url=http://localhost:8082"
)
@Import(SecurityConfig.class)
class SecurityConfigAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayCardPaymentService gatewayCardPaymentService;

    @MockitoBean
    private GatewayVirtualAccountService gatewayVirtualAccountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private InternalServiceTokenValidator internalServiceTokenValidator;

    @MockitoBean
    private PaymentProviderTokenValidator paymentProviderTokenValidator;

    @Test
    @WithMockUser(roles = "USER")
    void user_can_query_card_status() throws Exception {
        mockMvc.perform(get("/api/v1/payments/card/PAY-1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_can_reach_card_approve() throws Exception {
        mockMvc.perform(post("/api/v1/payments/card/approve"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_cannot_query_card_status_directly() throws Exception {
        mockMvc.perform(get("/api/v1/payments/card/PAY-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_service_cannot_query_card_status_directly() throws Exception {
        mockMvc.perform(get("/api/v1/payments/card/PAY-1")
                        .header(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_service_can_check_card_approval_history() throws Exception {
        mockMvc.perform(get("/api/v1/payments/card/internal/PAY-1/approval-exists")
                        .header(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannot_check_internal_card_approval_history() throws Exception {
        mockMvc.perform(get("/api/v1/payments/card/internal/PAY-1/approval-exists"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_cannot_approve_card_directly() throws Exception {
        mockMvc.perform(post("/api/v1/payments/card/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannot_call_card_refund() throws Exception {
        mockMvc.perform(post("/api/v1/payments/card/refund"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_service_can_reach_card_refund() throws Exception {
        mockMvc.perform(post("/api/v1/payments/card/refund")
                        .header(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannot_notify_virtual_account_deposit() throws Exception {
        mockMvc.perform(post("/api/v1/payments/virtual-account/deposit"))
                .andExpect(status().isForbidden());
    }

    @Test
    void payment_provider_can_reach_virtual_account_deposit() throws Exception {
        mockMvc.perform(post("/api/v1/payments/virtual-account/deposit")
                        .header(PaymentProviderAuthenticationFilter.PROVIDER_TOKEN_HEADER, "test-provider-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannot_issue_virtual_account_directly() throws Exception {
        mockMvc.perform(post("/api/v1/payments/virtual-account/issue"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_service_can_reach_virtual_account_issue() throws Exception {
        mockMvc.perform(post("/api/v1/payments/virtual-account/issue")
                        .header(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannot_query_virtual_account_status() throws Exception {
        mockMvc.perform(get("/api/v1/payments/virtual-account/internal/PAY-1/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_service_can_query_virtual_account_status() throws Exception {
        mockMvc.perform(get("/api/v1/payments/virtual-account/internal/PAY-1/status")
                        .header(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token"))
                .andExpect(status().isOk());
    }
}
