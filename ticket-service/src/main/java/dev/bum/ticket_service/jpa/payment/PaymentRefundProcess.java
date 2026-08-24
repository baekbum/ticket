package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "payment_refund_processes",
        indexes = {
                @Index(name = "idx_payment_refund_processes_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_refund_processes_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_payment_refund_processes_status", columnList = "status")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundProcess {

    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_process_id")
    private Long paymentRefundProcessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Column(name = "full_cancellation", nullable = false)
    private boolean fullCancellation;

    @Column(name = "selected_ticket_ids", nullable = false, length = 1000)
    private String selectedTicketIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundProcessStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_bank_company", length = 30)
    private BankCompany refundBankCompany;

    @Column(name = "refund_account_number_masked", length = 50)
    private String refundAccountNumberMasked;

    @Column(name = "refund_account_holder", length = 50)
    private String refundAccountHolder;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_tried_at")
    private LocalDateTime lastTriedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> selectedTickets,
            Integer refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        return PaymentRefundProcess.builder()
                .payment(payment)
                .reservation(payment.getReservation())
                .paymentNo(payment.getPaymentNo())
                .method(payment.getMethod())
                .refundAmount(refundAmount)
                .fullCancellation(fullCancellation)
                .selectedTicketIds(formatTicketIds(selectedTickets))
                .status(PaymentRefundProcessStatus.REQUESTED)
                .refundBankCompany(refundAccount != null ? refundAccount.getBankCompany() : null)
                .refundAccountNumberMasked(refundAccount != null ? maskAccountNumber(refundAccount.getAccountNumber()) : null)
                .refundAccountHolder(refundAccount != null ? refundAccount.getAccountHolder() : null)
                .retryCount(0)
                .lastTriedAt(LocalDateTime.now())
                .build();
    }

    public void gatewaySucceeded() {
        this.status = PaymentRefundProcessStatus.GATEWAY_SUCCEEDED;
        this.failureReason = null;
        this.lastTriedAt = LocalDateTime.now();
    }

    public void gatewayFailed(String failureReason) {
        this.status = PaymentRefundProcessStatus.GATEWAY_FAILED;
        this.failureReason = trimFailureReason(failureReason);
        this.retryCount = getRetryCount() + 1;
        this.lastTriedAt = LocalDateTime.now();
    }

    public void localSucceeded() {
        this.status = PaymentRefundProcessStatus.LOCAL_SUCCEEDED;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
    }

    public void localFailed(String failureReason) {
        this.status = PaymentRefundProcessStatus.LOCAL_FAILED;
        this.failureReason = trimFailureReason(failureReason);
    }

    public Integer getRetryCount() {
        return retryCount != null ? retryCount : 0;
    }

    public PaymentRefundProcessResponse toResponse() {
        return PaymentRefundProcessResponse.builder()
                .paymentRefundProcessId(this.paymentRefundProcessId)
                .paymentId(this.payment != null ? this.payment.getPaymentId() : null)
                .reservationId(this.reservation != null ? this.reservation.getReservationId() : null)
                .paymentNo(this.paymentNo)
                .method(this.method)
                .refundAmount(this.refundAmount)
                .fullCancellation(this.fullCancellation)
                .selectedTicketIds(this.selectedTicketIds)
                .status(this.status != null ? this.status.name() : null)
                .refundBankCompany(this.refundBankCompany)
                .refundAccountNumberMasked(this.refundAccountNumberMasked)
                .refundAccountHolder(this.refundAccountHolder)
                .failureReason(this.failureReason)
                .retryCount(getRetryCount())
                .lastTriedAt(formatDateTime(this.lastTriedAt))
                .completedAt(formatDateTime(this.completedAt))
                .createdAt(formatDateTime(this.createdAt))
                .updatedAt(formatDateTime(this.updatedAt))
                .build();
    }

    private static String formatTicketIds(List<Ticket> tickets) {
        return tickets.stream()
                .map(Ticket::getTicketId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String maskAccountNumber(String accountNumber) {
        if (!StringUtils.hasText(accountNumber)) {
            return null;
        }

        String normalizedAccountNumber = accountNumber.replaceAll("\\s+", "");
        if (normalizedAccountNumber.length() <= 4) {
            return "*".repeat(normalizedAccountNumber.length());
        }

        String lastDigits = normalizedAccountNumber.substring(normalizedAccountNumber.length() - 4);
        return "*".repeat(normalizedAccountNumber.length() - 4) + lastDigits;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }

    private String trimFailureReason(String failureReason) {
        if (!StringUtils.hasText(failureReason)) {
            return null;
        }
        return failureReason.length() > 1000 ? failureReason.substring(0, 1000) : failureReason;
    }
}
