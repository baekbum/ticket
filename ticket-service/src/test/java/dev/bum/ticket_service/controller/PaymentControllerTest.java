package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssueRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
    @DisplayName("카드 결제를 승인한다")
    void approve_card_payment() throws Exception {
        CardPaymentApproveRequest request = CardPaymentApproveRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .cardCompany("KB")
                .cardNumber("1234-5678-9012-3456")
                .cvc("123")
                .cardPassword("12")
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

        given(paymentService.approveCard("user01", request)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/card/approve")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value(request.getPaymentNo()))
                .andExpect(jsonPath("$.status").value("PAID"));

        then(paymentService).should().approveCard("user01", request);
    }

    @Test
    @DisplayName("가상계좌를 발급한다")
    void issue_virtual_account() throws Exception {
        VirtualAccountIssueRequest request = VirtualAccountIssueRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCode("KB")
                .build();
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo(request.getPaymentNo())
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .build();

        given(paymentService.issueVirtualAccount("user01", request)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/virtual-account/issue")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value(request.getPaymentNo()))
                .andExpect(jsonPath("$.status").value("WAITING_DEPOSIT"))
                .andExpect(jsonPath("$.accountNumber").value("1111-2222-3333-4444"));

        then(paymentService).should().issueVirtualAccount("user01", request);
    }

    @Test
    @DisplayName("관리자가 가상계좌 입금을 시뮬레이션한다")
    void deposit_virtual_account() throws Exception {
        VirtualAccountDepositRequest request = VirtualAccountDepositRequest.builder()
                .accountNumber("1111-2222-3333-4444")
                .depositorName("홍길동")
                .amount(180000)
                .build();
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber(request.getAccountNumber())
                .depositorName("홍길동")
                .build();

        given(paymentService.depositVirtualAccount(request)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/virtual-account/deposit")
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(request.getAccountNumber()))
                .andExpect(jsonPath("$.status").value("PAID"));

        then(paymentService).should().depositVirtualAccount(request);
    }

    private UsernamePasswordAuthenticationToken userAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private UsernamePasswordAuthenticationToken adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
