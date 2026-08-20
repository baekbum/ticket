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
import static org.mockito.Mockito.never;

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
        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.empty());
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
    @DisplayName("ticket-service 결제 완료 실패 시 카드 승인 취소 후 ticket 결제를 실패 처리한다")
    void cancel_card_approval_when_ticket_payment_completion_fails() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();

        given(dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                "IU",
                CardCompany.SHINHAN,
                "9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e"
        )).willReturn(Optional.of(dummyCard));
        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.empty());
        given(dummyCardPaymentHistoryJpaRepository.save(any(DummyCardPaymentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(passwordEncoder.matches("516", "cvc-hash")).willReturn(true);
        given(passwordEncoder.matches("1234", "password-hash")).willReturn(true);
        given(ticketPaymentClient.completeCardPayment(any())).willThrow(new IllegalStateException("ticket-service down"));

        assertThatThrownBy(() -> gatewayCardPaymentService.approve("IU", request))
                .isInstanceOf(TicketPaymentCompleteException.class)
                .hasMessage("카드 승인 후 ticket-service 반영에 실패해 카드 승인을 취소했습니다. 다시 결제해주세요.");

        ArgumentCaptor<DummyCardPaymentHistory> historyCaptor = ArgumentCaptor.forClass(DummyCardPaymentHistory.class);
        then(dummyCardPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo(CardPaymentHistoryStatus.CANCELLED);
        assertThat(historyCaptor.getValue().getFailureReason()).isEqualTo("ticket-service 결제 완료 반영 실패: ticket-service down");
        assertThat(dummyCard.getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        then(ticketPaymentClient).should().failCardPayment(any());
    }

    @Test
    @DisplayName("이미 실패 처리된 결제번호는 다시 카드 승인하지 않는다")
    void reject_reuse_of_failed_payment_no() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();
        DummyCardPaymentHistory existingHistory =
                DummyCardPaymentHistory.approved(dummyCard, request.getPaymentNo(), request.getAmount());
        existingHistory.cancel("ticket-service down");

        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.of(existingHistory));
        assertThatThrownBy(() -> gatewayCardPaymentService.approve("IU", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 카드 결제 시도가 처리된 결제번호입니다. 새 결제번호로 다시 시도해주세요.");

        assertThat(dummyCard.getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        then(dummyCardJpaRepository).shouldHaveNoInteractions();
        then(dummyCardPaymentHistoryJpaRepository).shouldHaveNoMoreInteractions();
        then(ticketPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기존 카드 승인 이력이 있어도 사용자 인증 정보가 없으면 거부한다")
    void reject_existing_history_without_current_user() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCardPaymentHistory existingHistory =
                DummyCardPaymentHistory.approved(dummyCard(), request.getPaymentNo(), request.getAmount());
        existingHistory.completeTicketPayment(null);

        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.of(existingHistory));

        assertThatThrownBy(() -> gatewayCardPaymentService.approve(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 인증 정보가 필요합니다.");

        then(dummyCardJpaRepository).shouldHaveNoInteractions();
        then(ticketPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카드 CVC 검증 실패 시 실패 이력을 저장하고 ticket-service를 호출하지 않는다")
    void save_failed_history_when_cvc_is_invalid() {
        GatewayCardPaymentApproveRequest request = approveRequest();
        DummyCard dummyCard = dummyCard();

        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.empty());
        given(dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                "IU",
                CardCompany.SHINHAN,
                "9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e"
        )).willReturn(Optional.of(dummyCard));
        given(dummyCardPaymentHistoryJpaRepository.save(any(DummyCardPaymentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(passwordEncoder.matches("516", "cvc-hash")).willReturn(false);

        assertThatThrownBy(() -> gatewayCardPaymentService.approve("IU", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카드 CVC가 일치하지 않습니다.");

        ArgumentCaptor<DummyCardPaymentHistory> historyCaptor = ArgumentCaptor.forClass(DummyCardPaymentHistory.class);
        then(dummyCardPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo(CardPaymentHistoryStatus.APPROVAL_FAILED);
        assertThat(historyCaptor.getValue().getFailureReason()).isEqualTo("카드 CVC가 일치하지 않습니다.");
        assertThat(historyCaptor.getValue().getPaymentNo()).isEqualTo(request.getPaymentNo());
        assertThat(historyCaptor.getValue().getDummyCard()).isEqualTo(dummyCard);
        assertThat(dummyCard.getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        then(ticketPaymentClient).should(never()).completeCardPayment(any());
        then(ticketPaymentClient).should().failCardPayment(any());
    }

    @Test
    @DisplayName("카드 조회 실패 시 카드 없이 실패 이력을 저장한다")
    void save_failed_history_without_card_when_card_not_found() {
        GatewayCardPaymentApproveRequest request = approveRequest();

        given(dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo()))
                .willReturn(Optional.empty());
        given(dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                "IU",
                CardCompany.SHINHAN,
                "9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e"
        )).willReturn(Optional.empty());
        given(dummyCardPaymentHistoryJpaRepository.save(any(DummyCardPaymentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> gatewayCardPaymentService.approve("IU", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카드 정보가 일치하지 않습니다.");

        ArgumentCaptor<DummyCardPaymentHistory> historyCaptor = ArgumentCaptor.forClass(DummyCardPaymentHistory.class);
        then(dummyCardPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo(CardPaymentHistoryStatus.APPROVAL_FAILED);
        assertThat(historyCaptor.getValue().getFailureReason()).isEqualTo("카드 정보가 일치하지 않습니다.");
        assertThat(historyCaptor.getValue().getDummyCard()).isNull();
        assertThat(historyCaptor.getValue().getCardNumberLast4()).isEqualTo("1111");
        then(ticketPaymentClient).should(never()).completeCardPayment(any());
        then(ticketPaymentClient).should().failCardPayment(any());
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
