package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.payment.payment.Payment;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/** 환불 작업의 최신 진행 상태와 재시도 정보. 요청 금액과 대상은 PaymentRefundSnapshot에 저장한다. */
@Entity
@Table(
        name = "payment_refund_processes",
        indexes = @Index(name = "idx_payment_refund_processes_status", columnList = "status"))
@Getter
@NoArgsConstructor
public class PaymentRefundProcess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_process_id")
    private Long paymentRefundProcessId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false, unique = true)
    private PaymentRefund refund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundProcessStatus status;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime lastTriedAt;
    private LocalDateTime completedAt;

    @org.hibernate.annotations.CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "paymentRefundProcess", cascade = CascadeType.ALL)
    @OrderBy("paymentRefundHistoryId ASC")
    private List<PaymentRefundHistory> histories = new ArrayList<>();

    @Builder
    public PaymentRefundProcess(
            Long paymentRefundProcessId,
            Payment payment,
            Reservation reservation,
            String paymentNo,
            PaymentMethod method,
            Integer refundAmount,
            Integer cancellationFeeAmount,
            boolean fullCancellation,
            String selectedTicketIds,
            PaymentRefundProcessStatus status,
            BankCompany refundBankCompany,
            String refundAccountNumberMasked,
            String refundAccountHolder,
            String failureReason,
            Integer retryCount,
            LocalDateTime lastTriedAt,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            PaymentRefund refund) {
        this.paymentRefundProcessId = paymentRefundProcessId;
        this.refund =
                refund == null
                        ? new PaymentRefund(
                                payment,
                                new PaymentRefundSnapshot(
                                        payment,
                                        selectedTicketIds,
                                        refundAmount,
                                        cancellationFeeAmount,
                                        fullCancellation,
                                        refundBankCompany,
                                        refundAccountNumberMasked,
                                        refundAccountHolder))
                        : refund;
        this.status = status == null ? PaymentRefundProcessStatus.REQUESTED : status;
        this.failureReason = failureReason;
        this.retryCount = retryCount == null ? 0 : retryCount;
        this.lastTriedAt = lastTriedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        histories.add(PaymentRefundHistory.event(this, PaymentRefundHistoryType.REQUESTED, null));
    }

    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            boolean full,
            RefundAccountRequest account) {
        return create(payment, tickets, amount, 0, full, account);
    }

    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            Integer fee,
            boolean full,
            RefundAccountRequest account) {
        return PaymentRefundProcess.builder()
                .refund(
                        new PaymentRefund(
                                payment,
                                new PaymentRefundSnapshot(
                                        payment, tickets, amount, fee, full, account)))
                .status(PaymentRefundProcessStatus.REQUESTED)
                .lastTriedAt(LocalDateTime.now())
                .build();
    }

    public Payment getPayment() {
        return refund.getPayment();
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
        return refund.getSnapshot().getRefundAmount();
    }

    public Integer getCancellationFeeAmount() {
        return refund.getSnapshot().getCancellationFeeAmount();
    }

    public boolean isFullCancellation() {
        return refund.getSnapshot().isFullCancellation();
    }

    public String getSelectedTicketIds() {
        return refund.getSnapshot().selectedTicketIds();
    }

    public BankCompany getRefundBankCompany() {
        return refund.getSnapshot().getRefundBankCompany();
    }

    public String getRefundAccountNumberMasked() {
        return refund.getSnapshot().getRefundAccountNumberMasked();
    }

    public String getRefundAccountHolder() {
        return refund.getSnapshot().getRefundAccountHolder();
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public List<PaymentRefundHistory> getHistories() {
        return Collections.unmodifiableList(histories);
    }

    private void move(
            PaymentRefundProcessStatus next, PaymentRefundHistoryType event, String reason) {
        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED) return;
        if (status == next && event != PaymentRefundHistoryType.RETRY_REQUESTED) return;
        PaymentRefundProcessStatus before = status;
        status = next;
        failureReason =
                reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        lastTriedAt = LocalDateTime.now();
        histories.add(PaymentRefundHistory.event(this, event, before));
    }

    public void gatewaySucceeded() {
        move(
                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED,
                PaymentRefundHistoryType.GATEWAY_SUCCEEDED,
                null);
    }

    public void gatewayRetryRequested() {
        move(PaymentRefundProcessStatus.REQUESTED, PaymentRefundHistoryType.RETRY_REQUESTED, null);
    }

    public void validateRefundAccount(RefundAccountRequest account) {
        refund.getSnapshot().validateAccount(account);
    }

    public void gatewayFailed(String reason) {
        if (status != PaymentRefundProcessStatus.LOCAL_SUCCEEDED) retryCount++;
        move(
                PaymentRefundProcessStatus.GATEWAY_FAILED,
                PaymentRefundHistoryType.GATEWAY_FAILED,
                reason);
    }

    public void localSucceeded() {
        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED) return;
        move(
                PaymentRefundProcessStatus.LOCAL_SUCCEEDED,
                PaymentRefundHistoryType.LOCAL_SUCCEEDED,
                null);
        refund.complete();
        completedAt = LocalDateTime.now();
    }

    public void localFailed(String reason) {
        move(
                PaymentRefundProcessStatus.LOCAL_FAILED,
                PaymentRefundHistoryType.LOCAL_FAILED,
                reason);
    }

    public PaymentRefundProcessResponse toResponse() {
        return PaymentRefundProcessResponse.builder()
                .paymentRefundProcessId(paymentRefundProcessId)
                .paymentId(getPayment().getPaymentId())
                .reservationId(getReservation().getReservationId())
                .paymentNo(getPaymentNo())
                .method(getMethod())
                .refundAmount(getRefundAmount())
                .fullCancellation(isFullCancellation())
                .selectedTicketIds(getSelectedTicketIds())
                .status(status.name())
                .refundBankCompany(getRefundBankCompany())
                .refundAccountNumberMasked(getRefundAccountNumberMasked())
                .refundAccountHolder(getRefundAccountHolder())
                .failureReason(failureReason)
                .retryCount(retryCount)
                .lastTriedAt(format(lastTriedAt))
                .completedAt(format(completedAt))
                .createdAt(format(createdAt))
                .updatedAt(format(updatedAt))
                .build();
    }

    private String format(LocalDateTime time) {
        return time == null
                ? null
                : time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
