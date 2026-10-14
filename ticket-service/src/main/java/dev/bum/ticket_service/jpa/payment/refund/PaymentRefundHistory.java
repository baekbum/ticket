package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundHistoryResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 환불 처리 사건을 누적한다. 요청 정보와 대상 티켓은 환불 스냅샷을 참조한다. */
@Entity
@Table(
        name = "payment_refund_histories",
        indexes =
                @Index(
                        name = "idx_refund_history_process",
                        columnList = "payment_refund_process_id"))
@Getter
@NoArgsConstructor
public class PaymentRefundHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_id")
    private Long paymentRefundHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_refund_process_id", nullable = false)
    private PaymentRefundProcess paymentRefundProcess;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentRefundHistoryType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentRefundProcessStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentRefundProcessStatus afterStatus;

    @Column(length = 1000)
    private String failureReason;

    @Column(name = "refunded_amount_after")
    private Integer refundedAmountAfter;

    @Column(name = "refundable_amount_after")
    private Integer refundableAmountAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status_after", length = 30)
    private PaymentStatus paymentStatusAfter;

    @Column(unique = true, length = 100)
    private String successKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PaymentRefundHistory event(
            PaymentRefundProcess process,
            PaymentRefundHistoryType type,
            PaymentRefundProcessStatus before) {
        PaymentRefundHistory h = new PaymentRefundHistory();
        h.paymentRefundProcess = process;
        h.eventType = type;
        h.beforeStatus = before;
        h.afterStatus = process.getStatus();
        h.failureReason = process.getFailureReason();
        h.createdAt = LocalDateTime.now();
        return h;
    }

    public static PaymentRefundHistory create(
            PaymentRefundProcess process,
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            boolean full) {
        PaymentRefundHistory h = event(process, PaymentRefundHistoryType.LOCAL_APPLIED, process.getStatus());
        h.refundedAmountAfter = payment.getRefundedAmount();
        h.refundableAmountAfter = payment.getRefundableAmount();
        h.paymentStatusAfter = payment.getStatus();
        h.successKey = "LOCAL_APPLIED:" + process.getPaymentRefundProcessId();
        return h;
    }

    public Payment getPayment() {
        return paymentRefundProcess.getPayment();
    }

    public Reservation getReservation() {
        return getPayment().getReservation();
    }

    public String getPaymentNo() {
        return getPayment().getPaymentNo();
    }

    public PaymentMethod getMethod() {
        return getPayment().getMethod();
    }

    public Integer getRefundAmount() {
        return paymentRefundProcess.getRefundAmount();
    }

    public boolean isFullCancellation() {
        return paymentRefundProcess.isFullCancellation();
    }

    public List<PaymentRefundSnapshotTicket> getTickets() {
        return paymentRefundProcess.getRefund().getSnapshot().getTickets();
    }

    public PaymentRefundHistoryResponse toResponse() {
        return PaymentRefundHistoryResponse.builder()
                .paymentRefundHistoryId(paymentRefundHistoryId)
                .paymentId(getPayment().getPaymentId())
                .reservationId(getReservation().getReservationId())
                .paymentNo(getPaymentNo())
                .method(getMethod())
                .refundAmount(getRefundAmount())
                .refundedAmountAfter(refundedAmountAfter)
                .refundableAmountAfter(refundableAmountAfter)
                .paymentStatusAfter(paymentStatusAfter)
                .fullCancellation(isFullCancellation())
                .tickets(
                        getTickets().stream().map(PaymentRefundSnapshotTicket::toResponse).toList())
                .createdAt(
                        createdAt == null
                                ? null
                                : createdAt.format(
                                        DateTimeFormatter.ofPattern(
                                                "yyyy-MM-dd HH:mm:ss")))
                .build();
    }
}
