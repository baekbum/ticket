package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.controller.payment.PaymentController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.payment.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PaymentService paymentService;

    private final String baseUrl = "/api/v1/payments";

    @Test
    @DisplayName("payment-gateway 카드 결제 완료 요청을 반영한다")
    void complete_card_payment_from_gateway() throws Exception {
        CardPaymentCompleteRequest request = CardPaymentCompleteRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .userId("user01")
                .amount(BigDecimal.valueOf(180000))
                .build();
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo(request.getPaymentNo())
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(180000)
                .build();

        given(paymentService.completeCardFromGateway(request)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/internal/card/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value(request.getPaymentNo()))
                .andExpect(jsonPath("$.status").value("PAID"));

        then(paymentService).should().completeCardFromGateway(request);
    }
}
