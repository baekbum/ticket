package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.controller.advice.GlobalExceptionHandler;
import dev.bum.ticket_service.controller.checkout.CheckoutController;
import dev.bum.ticket_service.exception.payment.GatewayAccountIssueUnconfirmedException;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutGatewayIssueFailureTest {
    @Test void exhaustedIssuanceReturns503WithAnUnconfirmedResultCodeAndKeepsTheCheckoutKey() throws Exception {
        CheckoutService checkout = mock(CheckoutService.class);
        when(checkout.confirm(isNull(), isNull(), any())).thenThrow(
                new GatewayAccountIssueUnconfirmedException(new IllegalStateException("upstream failed")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CheckoutController(checkout))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = """
                {"orderId":"ORDER-1","eventId":1,"seats":[],"paymentMethod":"BANK_TRANSFER",
                 "idempotencyKey":"idem-1","bankCode":"KB"}
                """;
        mvc.perform(post("/api/v1/checkout/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GATEWAY_ACCOUNT_ISSUE_UNCONFIRMED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("같은 결제 요청")));
        verify(checkout).confirm(isNull(), isNull(), argThat(request -> "idem-1".equals(request.getIdempotencyKey())));
    }
}
