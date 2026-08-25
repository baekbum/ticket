package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.controller.payment.PaymentRefundProcessManagementController;
import dev.bum.ticket_service.security.InternalServiceTokenValidator;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessService;
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
@WebMvcTest(PaymentRefundProcessManagementController.class)
class PaymentRefundProcessManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private InternalServiceTokenValidator internalServiceTokenValidator;

    @MockitoBean
    private PaymentRefundProcessService paymentRefundProcessService;

    private final String baseUrl = "/api/v1/manage/payment/refund-process";

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("관리자가 환불 처리 현황을 조건으로 조회한다")
    void payment_refund_process_select_by_cond() throws Exception {
        PaymentRefundProcessCondRequest cond = PaymentRefundProcessCondRequest.builder()
                .status("LOCAL_FAILED")
                .page(0)
                .size(10)
                .build();
        CustomPageResponse<PaymentRefundProcessResponse> response = CustomPageResponse.of(
                List.of(PaymentRefundProcessResponse.builder()
                        .paymentRefundProcessId(1L)
                        .reservationId(10L)
                        .paymentNo("PAY-1")
                        .method(PaymentMethod.CREDIT_CARD)
                        .status("LOCAL_FAILED")
                        .refundAmount(100000)
                        .build()),
                10,
                0,
                1,
                1
        );

        given(paymentRefundProcessService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentRefundProcessId").value(1L))
                .andExpect(jsonPath("$.content[0].status").value("LOCAL_FAILED"));

        then(paymentRefundProcessService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("관리자가 로컬 환불 반영을 수동 완료 처리한다")
    void payment_refund_process_complete_local() throws Exception {
        PaymentRefundProcessResponse response = PaymentRefundProcessResponse.builder()
                .paymentRefundProcessId(1L)
                .status("LOCAL_SUCCEEDED")
                .build();
        given(paymentRefundProcessService.completeLocal(1L)).willReturn(response);

        mockMvc.perform(put(baseUrl + "/local-complete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCAL_SUCCEEDED"));

        then(paymentRefundProcessService).should().completeLocal(1L);
    }
}
