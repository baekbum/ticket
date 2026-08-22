package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayCardPaymentRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.jpa.payment.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CardPaymentRefundServiceTest {

    @Mock
    private PaymentGatewayCardClient paymentGatewayCardClient;

    @InjectMocks
    private CardPaymentRefundService cardPaymentRefundService;

    @Test
    @DisplayName("카드 전체 환불 요청 후 결제를 전체 환불 상태로 변경한다")
    void refund_all_card_payment() {
        Payment payment = cardPayment();

        cardPaymentRefundService.refundAll(payment);

        ArgumentCaptor<GatewayCardPaymentRefundRequest> captor = ArgumentCaptor.forClass(GatewayCardPaymentRefundRequest.class);
        then(paymentGatewayCardClient).should().refund(captor.capture());
        assertThat(captor.getValue().getPaymentNo()).isEqualTo("PAY-1");
        assertThat(captor.getValue().getTransactionId()).isEqualTo("CARD-1");
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("250000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(payment.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("카드 부분 환불 요청 후 결제를 부분 환불 상태로 변경한다")
    void refund_partial_card_payment() {
        Payment payment = cardPayment();

        cardPaymentRefundService.refundPartial(payment, 125000);

        ArgumentCaptor<GatewayCardPaymentRefundRequest> captor = ArgumentCaptor.forClass(GatewayCardPaymentRefundRequest.class);
        then(paymentGatewayCardClient).should().refund(captor.capture());
        assertThat(captor.getValue().getPaymentNo()).isEqualTo("PAY-1");
        assertThat(captor.getValue().getTransactionId()).isEqualTo("CARD-1");
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("125000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(125000);
        assertThat(payment.getRemainingAmount()).isEqualTo(125000);
    }

    @Test
    @DisplayName("카드 결제 완료 상태가 아니면 전체 환불할 수 없다")
    void reject_not_paid_card_payment() {
        Payment payment = cardPayment(PaymentStatus.FAILED);

        assertThatThrownBy(() -> cardPaymentRefundService.refundAll(payment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불할 수 없는 결제 상태입니다.");

        then(paymentGatewayCardClient).shouldHaveNoInteractions();
    }

    private Payment cardPayment() {
        return cardPayment(PaymentStatus.PAID);
    }

    private Payment cardPayment(PaymentStatus status) {
        return Payment.builder()
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(status)
                .amount(250000)
                .cardInfo(CardPaymentInfo.builder()
                        .transactionId("CARD-1")
                        .cardCompany(CardCompany.SHINHAN)
                        .maskedCardNumber("4111-****-****-1111")
                        .build())
                .requestedAt(LocalDateTime.of(2026, 8, 21, 12, 0))
                .build();
    }
}
