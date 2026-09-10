package dev.bum.payment_gateway_service.service.card;

import dev.bum.payment_gateway_service.controller.advice.GlobalExceptionHandler;
import dev.bum.payment_gateway_service.controller.card.GatewayCardPaymentController;
import dev.bum.payment_gateway_service.exception.CardPaymentPendingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GatewayCardHttpContractTest {
    @Test
    void pendingIs202WithPaymentNumberAndIsNotApprovalSuccess() throws Exception {
        GatewayCardPaymentService payments = mock(GatewayCardPaymentService.class);
        when(payments.approve(any(), any())).thenThrow(new CardPaymentPendingException("PAY-1", new RuntimeException()));
        MockMvcBuilders.standaloneSetup(new GatewayCardPaymentController(payments))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(post("/api/v1/payments/card/approve").contentType(MediaType.APPLICATION_JSON).content("""
                        {"paymentNo":"PAY-1","cardCompany":"SHINHAN","cardNumber":"4111111111111111",
                        "cvc":"123","cardPassword":"1234","customerName":"user","amount":10000}
                        """))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.paymentNo").value("PAY-1"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.approved").doesNotExist());
    }
}
