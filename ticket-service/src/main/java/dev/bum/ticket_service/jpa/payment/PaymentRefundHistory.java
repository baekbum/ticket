package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundHistoryResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "payment_refund_histories",
        indexes = {
                @Index(name = "idx_payment_refund_histories_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_refund_histories_reservation_id", columnList = "reservation_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundHistory {

    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_id")
    private Long paymentRefundHistoryId;

    // 환불이 발생한 결제 원본.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 환불이 발생한 예매 원본.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // gateway 환불 요청과 추적에 사용하는 결제 식별 번호 스냅샷.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 환불된 결제 수단 스냅샷.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    // 이번 환불 이벤트에서 실제 환불한 금액.
    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    // 이번 환불 처리 후 결제의 누적 환불 금액.
    @Column(name = "refunded_amount_after", nullable = false)
    private Integer refundedAmountAfter;

    // 이번 환불 처리 후 추가로 환불 가능한 금액.
    @Column(name = "refundable_amount_after", nullable = false)
    private Integer refundableAmountAfter;

    // 이번 환불 처리 후 결제 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status_after", nullable = false, length = 30)
    private PaymentStatus paymentStatusAfter;

    // 예매의 남은 활성 티켓을 모두 취소한 환불인지 여부.
    @Column(name = "full_cancellation", nullable = false)
    private boolean fullCancellation;

    // 이번 환불 이벤트에 포함된 티켓 목록.
    @OneToMany(mappedBy = "paymentRefundHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentRefundHistoryTicket> tickets = new ArrayList<>();

    // 환불 이력이 저장된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PaymentRefundHistory create(
            Payment payment,
            List<Ticket> tickets,
            Integer refundAmount,
            boolean fullCancellation
    ) {
        PaymentRefundHistory history = PaymentRefundHistory.builder()
                .payment(payment)
                .reservation(payment.getReservation())
                .paymentNo(payment.getPaymentNo())
                .method(payment.getMethod())
                .refundAmount(refundAmount)
                .refundedAmountAfter(payment.getRefundedAmount())
                .refundableAmountAfter(payment.getRefundableAmount())
                .paymentStatusAfter(payment.getStatus())
                .fullCancellation(fullCancellation)
                .build();

        tickets.forEach(history::addTicket);
        return history;
    }

    private void addTicket(Ticket ticket) {
        PaymentRefundHistoryTicket historyTicket = PaymentRefundHistoryTicket.create(this, ticket);
        this.tickets.add(historyTicket);
    }

    public PaymentRefundHistoryResponse toResponse() {
        return PaymentRefundHistoryResponse.builder()
                .paymentRefundHistoryId(this.paymentRefundHistoryId)
                .paymentId(this.payment != null ? this.payment.getPaymentId() : null)
                .reservationId(this.reservation != null ? this.reservation.getReservationId() : null)
                .paymentNo(this.paymentNo)
                .method(this.method)
                .refundAmount(this.refundAmount)
                .refundedAmountAfter(this.refundedAmountAfter)
                .refundableAmountAfter(this.refundableAmountAfter)
                .paymentStatusAfter(this.paymentStatusAfter)
                .fullCancellation(this.fullCancellation)
                .tickets(this.tickets.stream()
                        .map(PaymentRefundHistoryTicket::toResponse)
                        .toList())
                .createdAt(formatDateTime(this.createdAt))
                .build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}
