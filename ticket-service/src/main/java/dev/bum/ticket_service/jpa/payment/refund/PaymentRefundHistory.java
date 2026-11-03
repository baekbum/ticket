package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundHistoryResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

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

    // 결제 환불 이력 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_id")
    private Long paymentRefundHistoryId;

    // 결제 환불 진행 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_refund_process_id", nullable = false)
    private PaymentRefundProcess paymentRefundProcess;

    // 이벤트 종류.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentRefundHistoryType eventType;

    // 변경 전 상태.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentRefundProcessStatus beforeStatus;

    // 변경 후 상태.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentRefundProcessStatus afterStatus;

    // 실패 사유.
    @Column(length = 1000)
    private String failureReason;

    // 처리 후 누적 환불 금액.
    @Column(name = "refunded_amount_after")
    private Integer refundedAmountAfter;

    // 처리 후 환불 가능 잔액.
    @Column(name = "refundable_amount_after")
    private Integer refundableAmountAfter;

    // 처리 후 결제 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status_after", length = 30)
    private PaymentStatus paymentStatusAfter;

    // 성공한 요청의 멱등성 키.
    @Column(unique = true, length = 100)
    private String successKey;

    // 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 이력에 사용할 이벤트 정보를 구성한다.
    public static PaymentRefundHistory event(
            PaymentRefundProcess process,
            PaymentRefundHistoryType type,
            PaymentRefundProcessStatus before
    ) {

        PaymentRefundHistory h = new PaymentRefundHistory();
        h.paymentRefundProcess = process;
        h.eventType = type;
        h.beforeStatus = before;
        h.afterStatus = process.getStatus();
        h.failureReason = process.getFailureReason();
        h.createdAt = LocalDateTime.now();
        return h;
    }

    // 결제 환불 이력을 생성한다.
    public static PaymentRefundHistory create(
            PaymentRefundProcess process,
            Payment payment
    ) {

        PaymentRefundHistory h = event(process, PaymentRefundHistoryType.LOCAL_APPLIED, process.getStatus());
        h.refundedAmountAfter = payment.getRefundedAmount();
        h.refundableAmountAfter = payment.getRefundableAmount();
        h.paymentStatusAfter = payment.getStatus();
        h.successKey = "LOCAL_APPLIED:" + process.getPaymentRefundProcessId();
        return h;
    }

    // 결제 정보 값을 반환한다.
    public Payment getPayment() {

        return paymentRefundProcess.getPayment();
    }

    // 예매 정보 값을 반환한다.
    public Reservation getReservation() {

        return getPayment().getReservation();
    }

    // 결제 번호 값을 반환한다.
    public String getPaymentNo() {

        return getPayment().getPaymentNo();
    }

    // 결제 또는 요청 방식 값을 반환한다.
    public PaymentMethod getMethod() {

        return getPayment().getMethod();
    }

    // 환불 금액 값을 반환한다.
    public Integer getRefundAmount() {

        return paymentRefundProcess.getRefundAmount();
    }

    // 전체 취소 여부 여부를 확인한다.
    public boolean isFullCancellation() {

        return paymentRefundProcess.isFullCancellation();
    }

    // 티켓 목록 값을 반환한다.
    public List<PaymentRefundSnapshotTicket> getTickets() {

        return paymentRefundProcess.getRefund().getSnapshot().getTickets();
    }

    // 외부 응답에 사용할 DTO로 변환한다.
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
