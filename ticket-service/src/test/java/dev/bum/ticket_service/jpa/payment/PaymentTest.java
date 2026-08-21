package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    @DisplayName("전체 환불 시 누적 환불 금액은 최초 결제 금액과 같고 남은 금액은 0원이다")
    void refund_all_amount() {
        Payment payment = payment(250000);

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(payment.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("부분 환불 시 누적 환불 금액과 남은 결제 금액을 계산한다")
    void partial_refund_amount() {
        Payment payment = payment(250000);

        payment.partialRefund(120000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(120000);
        assertThat(payment.getRemainingAmount()).isEqualTo(130000);
    }

    @Test
    @DisplayName("부분 환불 누적 금액이 최초 결제 금액과 같아지면 전체 환불 상태가 된다")
    void partial_refund_to_all_refunded() {
        Payment payment = payment(250000);

        payment.partialRefund(120000);
        payment.partialRefund(130000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(payment.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("남은 결제 금액보다 큰 금액은 환불할 수 없다")
    void reject_refund_amount_exceeding_remaining_amount() {
        Payment payment = payment(250000);

        assertThatThrownBy(() -> payment.partialRefund(250001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불 금액이 남은 결제 금액을 초과했습니다.");
    }

    private Payment payment(int amount) {
        return Payment.builder()
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(amount)
                .requestedAt(LocalDateTime.of(2026, 8, 21, 12, 0))
                .build();
    }
}
