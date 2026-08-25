package dev.bum.admin_service.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.payment.PaymentRefundProcessServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminPaymentRefundProcessController.class)
class AdminPaymentRefundProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PaymentRefundProcessServiceClient paymentRefundProcessServiceClient;

    private final String baseUrl = "/api/v1/payment/refund-process";

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("관리자가 환불 처리 현황을 조회한다")
    void payment_refund_process_select_by_cond() throws Exception {
        PaymentRefundProcessCondRequest cond = PaymentRefundProcessCondRequest.builder()
                .method(PaymentMethod.CREDIT_CARD)
                .status("GATEWAY_FAILED")
                .build();
        CustomPageResponse<PaymentRefundProcessResponse> response = CustomPageResponse.of(
                List.of(PaymentRefundProcessResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .method(PaymentMethod.CREDIT_CARD)
                        .status("GATEWAY_FAILED")
                        .refundAmount(100000)
                        .build()),
                10,
                0,
                1,
                1
        );

        given(paymentRefundProcessServiceClient.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentRefundProcessId").value(1L))
                .andExpect(jsonPath("$.content[0].status").value("GATEWAY_FAILED"));

        then(paymentRefundProcessServiceClient).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("관리자가 로컬 환불 반영을 수동 완료 처리한다")
    void payment_refund_process_complete_local() throws Exception {
        PaymentRefundProcessResponse response = PaymentRefundProcessResponse.builder()
                .paymentRefundProcessId(1L)
                .status("LOCAL_SUCCEEDED")
                .build();
        given(paymentRefundProcessServiceClient.completeLocal(1L)).willReturn(response);

        mockMvc.perform(put(baseUrl + "/local-complete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCAL_SUCCEEDED"));

        then(paymentRefundProcessServiceClient).should().completeLocal(1L);
    }
}
