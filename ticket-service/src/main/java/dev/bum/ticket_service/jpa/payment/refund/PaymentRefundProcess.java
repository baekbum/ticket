package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 환불 작업의 최신 진행 상태와 재시도 정보. 요청 금액과 대상은 PaymentRefundSnapshot에 저장한다. */
@Entity
@DynamicUpdate
@Table(
        name = "payment_refund_processes",
        indexes = @Index(name = "idx_payment_refund_processes_status", columnList = "status"))
@Getter
@NoArgsConstructor
public class PaymentRefundProcess {

    // 결제 환불 처리 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_process_id")
    private Long paymentRefundProcessId;

    // 환불 정보.
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false, unique = true)
    private PaymentRefund refund;

    // 결제 환불 처리 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundProcessStatus status;

    // 실패 사유.
    @Column(length = 1000)
    private String failureReason;

    // 재시도 횟수.
    @Column(nullable = false)
    private int retryCount;

    // 마지막 시도 시각.
    private LocalDateTime lastTriedAt;

    // 처리 완료 시각.
    private LocalDateTime completedAt;

    // 생성 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 처리 이력 목록.
    @OneToMany(mappedBy = "paymentRefundProcess", cascade = CascadeType.ALL)
    @OrderBy("paymentRefundHistoryId ASC")
    private List<PaymentRefundHistory> histories = new ArrayList<>();

    // 전달받은 값으로 결제 환불 처리 객체를 초기화한다.
    @Builder
    public PaymentRefundProcess(
            Long paymentRefundProcessId,
            Payment payment,
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
            PaymentRefund refund
    ) {

        this.paymentRefundProcessId = paymentRefundProcessId;
        this.refund =
                refund == null
                        ? PaymentRefund.builder()
                                .payment(payment)
                                .snapshot(
                                        PaymentRefundSnapshot.legacyBuilder()
                                                .payment(payment)
                                                .ids(selectedTicketIds)
                                                .amount(refundAmount)
                                                .fee(cancellationFeeAmount)
                                                .full(fullCancellation)
                                                .bank(refundBankCompany)
                                                .masked(refundAccountNumberMasked)
                                                .holder(refundAccountHolder)
                                                .build()
                                )
                                .build()
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

    // 결제 환불 처리를 생성한다.
    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            boolean full,
            RefundAccountRequest account
    ) {

        return create(payment, tickets, amount, 0, full, account);
    }

    // 결제 환불 처리를 생성한다.
    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            Integer fee,
            boolean full,
            RefundAccountRequest account
    ) {

        return create(payment, tickets, amount, fee, full, account, LocalDateTime.now(), false);
    }

    // 결제 환불 처리를 생성한다.
    public static PaymentRefundProcess create(
            Payment payment,
            List<Ticket> tickets,
            Integer amount,
            Integer fee,
            boolean full,
            RefundAccountRequest account,
            LocalDateTime capturedAt,
            boolean restoreCoupon
    ) {

        return PaymentRefundProcess.builder()
                .refund(
                        PaymentRefund.builder()
                                .payment(payment)
                                .snapshot(
                                        PaymentRefundSnapshot.builder()
                                                .payment(payment)
                                                .tickets(tickets)
                                                .refundAmount(amount)
                                                .cancellationFeeAmount(fee)
                                                .fullCancellation(full)
                                                .refundAccount(account)
                                                .capturedAt(capturedAt)
                                                .restoreCoupon(restoreCoupon)
                                                .build()
                                )
                                .build())
                .status(PaymentRefundProcessStatus.REQUESTED)
                .lastTriedAt(LocalDateTime.now())
                .build();
    }

    // 결제 정보 값을 반환한다.
    public Payment getPayment() {

        return refund.getPayment();
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

        return refund.getSnapshot().getRefundAmount();
    }

    // 취소 수수료 금액 값을 반환한다.
    public Integer getCancellationFeeAmount() {

        return refund.getSnapshot().getCancellationFeeAmount();
    }

    // 전체 취소 여부 여부를 확인한다.
    public boolean isFullCancellation() {

        return refund.getSnapshot().isFullCancellation();
    }

    // 선택한 티켓 식별자 목록 값을 반환한다.
    public String getSelectedTicketIds() {

        return refund.getSnapshot().selectedTicketIds();
    }

    // 환불받을 은행 값을 반환한다.
    public BankCompany getRefundBankCompany() {

        return refund.getSnapshot().getRefundBankCompany();
    }

    // 마스킹한 환불 계좌 번호 값을 반환한다.
    public String getRefundAccountNumberMasked() {

        return refund.getSnapshot().getRefundAccountNumberMasked();
    }

    // 환불 계좌 예금주 값을 반환한다.
    public String getRefundAccountHolder() {

        return refund.getSnapshot().getRefundAccountHolder();
    }

    // 재시도 횟수 값을 반환한다.
    public Integer getRetryCount() {

        return retryCount;
    }

    // 처리 이력 목록 값을 반환한다.
    public List<PaymentRefundHistory> getHistories() {

        return Collections.unmodifiableList(histories);
    }

    // 결제 환불 처리 처리에서 연결된 위치 또는 대상을 변경한다.
    private void move(
            PaymentRefundProcessStatus next,
            PaymentRefundHistoryType event,
            String reason
    ) {

        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED || status == PaymentRefundProcessStatus.SUPERSEDED) return;

        if (status == next && event != PaymentRefundHistoryType.RETRY_REQUESTED) return;
        PaymentRefundProcessStatus before = status;
        status = next;
        failureReason =
                reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        lastTriedAt = LocalDateTime.now();
        histories.add(PaymentRefundHistory.event(this, event, before));
    }

    // 게이트웨이 처리 성공 결과를 반영한다.
    public void gatewaySucceeded() {

        move(
                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED,
                PaymentRefundHistoryType.GATEWAY_SUCCEEDED,
                null);
    }

    // 기존 요청이 대체되었음을 반영한다.
    public void superseded() {

        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED || status == PaymentRefundProcessStatus.SUPERSEDED) return;
        move(PaymentRefundProcessStatus.SUPERSEDED, PaymentRefundHistoryType.SUPERSEDED, null);
        refund.supersede();
    }

    // 게이트웨이 재시도 요청 상태를 반영한다.
    public void gatewayRetryRequested() {

        move(PaymentRefundProcessStatus.REQUESTED, PaymentRefundHistoryType.RETRY_REQUESTED, null);
    }

    // 환불에 필요한 은행과 계좌 정보를 검증한다.
    public void validateRefundAccount(RefundAccountRequest account) {

        refund.getSnapshot().validateAccount(account);
    }

    // 게이트웨이 처리 실패 결과를 반영한다.
    public void gatewayFailed(String reason) {

        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED || status == PaymentRefundProcessStatus.SUPERSEDED) return;
        retryCount++;
        move(
                PaymentRefundProcessStatus.GATEWAY_FAILED,
                PaymentRefundHistoryType.GATEWAY_FAILED,
                reason);
    }

    // 로컬 데이터 반영 성공 결과를 기록한다.
    public void localSucceeded() {

        if (status == PaymentRefundProcessStatus.LOCAL_SUCCEEDED || status == PaymentRefundProcessStatus.SUPERSEDED) return;
        move(
                PaymentRefundProcessStatus.LOCAL_SUCCEEDED,
                PaymentRefundHistoryType.LOCAL_SUCCEEDED,
                null);
        refund.complete();
        completedAt = LocalDateTime.now();
    }

    // 로컬 데이터 반영 실패 결과를 기록한다.
    public void localFailed(String reason) {

        move(
                PaymentRefundProcessStatus.LOCAL_FAILED,
                PaymentRefundHistoryType.LOCAL_FAILED,
                reason);
    }

    // 외부 응답에 사용할 DTO로 변환한다.
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

    // 값을 표시용 문자열로 변환한다.
    private String format(LocalDateTime time) {

        return time == null
                ? null
                : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
