package dev.bum.payment_gateway_service.service.card;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;
import dev.bum.payment_gateway_service.exception.TicketPaymentCompleteException;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.jpa.card.DummyCard;
import dev.bum.payment_gateway_service.jpa.card.DummyCardJpaRepository;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistory;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GatewayCardPaymentServiceTest {

    @Mock
    private DummyCardJpaRepository dummyCardJpaRepository;

    @Mock
    private DummyCardPaymentHistoryJpaRepository dummyCardPaymentHistoryJpaRepository;

    @Mock
    private TicketPaymentClient ticketPaymentClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private GatewayCardPaymentService gatewayCardPaymentService;

    @Test
    @DisplayName("카드 승인 성공 후 ticket-service 결제 완료까지 반영한다")
    void approve_card_payment_and_complete_ticket_payment() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();
        PaymentResponse ticketPayment = ticketPayment(PaymentStatus.PAID);

        given(dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                "IU",
                CardCompany.SHINHAN,
                "9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e"
        )).willReturn(Optional.of(dummyCard));
        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo())).willReturn(Optional.empty());
        given(dummyCardPaymentHistoryJpaRepository.save(any(DummyCardPaymentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(passwordEncoder.matches("516", "cvc-hash")).willReturn(true);
        given(passwordEncoder.matches("1234", "password-hash")).willReturn(true);
        given(ticketPaymentClient.completeCardPayment(any())).willReturn(ticketPayment);

        GatewayCardPaymentApproveResponse response = gatewayCardPaymentService.approve("IU", request);

        assertThat(response.getApproved()).isTrue();
        assertThat(response.getPaymentNo()).isEqualTo(request.getPaymentNo());
        assertThat(response.getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");

        ArgumentCaptor<DummyCardPaymentHistory> historyCaptor = ArgumentCaptor.forClass(DummyCardPaymentHistory.class);
        then(dummyCardPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED);
        then(ticketPaymentClient).should().completeCardPayment(any());
    }

    @Test
    @DisplayName("ticket-service 결제 완료 실패 시 승인 이력을 실패 상태로 변경한다")
    void mark_history_failed_when_ticket_payment_completion_fails() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();

        given(dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                "IU",
                CardCompany.SHINHAN,
                "9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e"
        )).willReturn(Optional.of(dummyCard));
        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo())).willReturn(Optional.empty());
        given(dummyCardPaymentHistoryJpaRepository.save(any(DummyCardPaymentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(passwordEncoder.matches("516", "cvc-hash")).willReturn(true);
        given(passwordEncoder.matches("1234", "password-hash")).willReturn(true);
        given(ticketPaymentClient.completeCardPayment(any())).willThrow(new IllegalStateException("ticket-service down"));

        assertThatThrownBy(() -> gatewayCardPaymentService.approve("IU", request))
                .isInstanceOf(TicketPaymentCompleteException.class)
                .hasMessage("ticket-service 결제 완료 반영에 실패했습니다.");

        ArgumentCaptor<DummyCardPaymentHistory> historyCaptor = ArgumentCaptor.forClass(DummyCardPaymentHistory.class);
        then(dummyCardPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_FAILED);
        assertThat(historyCaptor.getValue().getFailureReason()).isEqualTo("ticket-service down");
    }

    @Test
    @DisplayName("이미 승인된 결제는 카드 한도 차감 없이 ticket-service 완료 반영만 재시도한다")
    void retry_ticket_payment_completion_without_card_reapproval() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();
        DummyCardPaymentHistory existingHistory =
                DummyCardPaymentHistory.approved(dummyCard, request.getPaymentNo(), request.getAmount());
        existingHistory.failTicketPayment("ticket-service down");

        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.of(existingHistory));
        given(ticketPaymentClient.completeCardPayment(any())).willReturn(ticketPayment(PaymentStatus.PAID));

        GatewayCardPaymentApproveResponse response = gatewayCardPaymentService.approve("IU", request);

        assertThat(response.getApproved()).isTrue();
        assertThat(existingHistory.getStatus()).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED);
        assertThat(dummyCard.getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        then(dummyCardJpaRepository).shouldHaveNoInteractions();
        then(dummyCardPaymentHistoryJpaRepository).shouldHaveNoMoreInteractions();
    }

    private GatewayCardPaymentApproveRequest approveRequest() {
        return GatewayCardPaymentApproveRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .cardCompany(CardCompany.SHINHAN)
                .cardNumber("4111-1111-1111-1111")
                .cvc("516")
                .cardPassword("1234")
                .customerName("아이유")
                .amount(BigDecimal.valueOf(10000))
                .build();
    }

    private DummyCard dummyCard() {
        return DummyCard.builder()
                .dummyCardId(1L)
                .userId("IU")
                .cardCompany(CardCompany.SHINHAN)
                .cardNumberHash("9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e")
                .cardNumberLast4("1111")
                .cvcHash("cvc-hash")
                .cardPasswordHash("password-hash")
                .customerName("아이유")
                .issuedAt(LocalDate.of(2024, 1, 1))
                .expiresAt(LocalDate.of(2030, 12, 31))
                .currentMonthUsedAmount(BigDecimal.ZERO)
                .limitAmount(BigDecimal.valueOf(100000))
                .active(true)
                .build();
    }

    private PaymentResponse ticketPayment(PaymentStatus status) {
        return PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.CREDIT_CARD)
                .status(status)
                .amount(10000)
                .build();
    }
}
