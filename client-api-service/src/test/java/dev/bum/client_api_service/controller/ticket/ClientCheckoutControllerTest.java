package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.feign.ticket.TicketCheckoutServiceClient;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientCheckoutControllerTest {
    @Test
    void confirm_forwards_bank_and_idempotency_key_and_returns_account() throws Exception {
        TicketCheckoutServiceClient client = mock(TicketCheckoutServiceClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientCheckoutController(client)).build();
        when(client.confirm(eq("Bearer token"), eq("active-token"), any())).thenReturn(PaymentResponse.builder()
                .status(PaymentStatus.WAITING_DEPOSIT)
                .bankName("토스뱅크")
                .accountNumber("8888-1234")
                .amount(184000)
                .expiresAt("2026-09-09 23:59:59")
                .build());

        mvc.perform(post("/api/v1/checkout/confirm")
                        .header("Authorization", "Bearer token")
                        .header("X-Active-Token", "active-token")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-1","eventId":1,"seats":[{"id":1}],
                                 "delivery":null,"paymentMethod":"BANK_TRANSFER",
                                 "bankCode":"TOSS","idempotencyKey":"CHK-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("WAITING_DEPOSIT"))
                .andExpect(jsonPath("bankName").value("토스뱅크"))
                .andExpect(jsonPath("accountNumber").value("8888-1234"))
                .andExpect(jsonPath("amount").value(184000))
                .andExpect(jsonPath("expiresAt").value("2026-09-09 23:59:59"));
        verify(client).confirm(eq("Bearer token"), eq("active-token"), argThat(request ->
                "TOSS".equals(request.getBankCode())
                        && "CHK-1".equals(request.getIdempotencyKey())
                        && request.getDelivery() == null));
    }
}
