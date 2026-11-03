package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.virtualAccount.VirtualAccountPaymentInfo;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "payments",
        indexes = {
            @Index(
                    name = "idx_payments_idempotency_key_status",
                    columnList = "idempotency_key,status")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_payments_reservation_id", columnNames = "reservation_id"),
            @UniqueConstraint(name = "uk_payments_payment_no", columnNames = "payment_no"),
            @UniqueConstraint(name = "uk_payments_account_number", columnNames = "account_number"),
            @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
        })
@Getter
@NoArgsConstructor
public class Payment {

    // 날짜와 시각을 표시하는 형식.
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 결제 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    // 결제가 연결된 예약. 현재 구조에서는 예약 1건당 결제 1건을 기준으로 둔다.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 사용자나 외부 API가 입금/결제 완료 요청을 보낼 때 사용하는 결제 식별 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 무통장, 신용카드, 간편결제 같은 결제 수단.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    // 결제 대기, 입금 대기, 완료, 만료, 취소 등 결제의 현재 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    // 결제 완료 이후 누적 환불 금액.
    @Column(name = "refunded_amount", nullable = false)
    private Integer refundedAmount = 0;

    // 취소 과정에서 환불하지 않고 확정한 누적 취소 수수료.
    @Column(name = "cancellation_fee_amount", nullable = false)
    private Integer cancellationFeeAmount = 0;

    // 같은 결제 요청이 중복 처리되지 않도록 클라이언트나 서버가 발급하는 멱등성 키.
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // 카드 승인 결과 스냅샷. 카드 결제에서만 사용한다.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
                name = "transactionId",
                column = @Column(name = "card_transaction_id", length = 80)),
        @AttributeOverride(
                name = "cardCompany",
                column = @Column(name = "card_company", length = 30)),
        @AttributeOverride(
                name = "maskedCardNumber",
                column = @Column(name = "card_number_masked", length = 30))
    })
    private CardPaymentInfo cardInfo;

    // 가상계좌 발급/입금 결과 스냅샷. 무통장 결제에서만 사용한다.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "bankName", column = @Column(name = "bank_name", length = 50)),
        @AttributeOverride(
                name = "accountNumber",
                column = @Column(name = "account_number", length = 50)),
        @AttributeOverride(
                name = "depositorName",
                column = @Column(name = "depositor_name", length = 50))
    })
    private VirtualAccountPaymentInfo virtualAccountInfo;

    // 결제 요청이 생성된 시각.
    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 실제 결제가 완료된 시각.
    private LocalDateTime paidAt;

    // 결제 대기 만료 시각. 무통장 입금 만료나 카드 결제 제한 시간에 사용한다.
    private LocalDateTime expiresAt;

    // 결제 row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 결제 상태나 입금 정보가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 처리 시점의 데이터 스냅샷.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_snapshot_id", nullable = false, unique = true)
    private PaymentSnapshot snapshot;

    // 처리 진행 정보.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_process_id", nullable = false, unique = true)
    private PaymentProcess process;

    // 처리 이력 목록.
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("paymentHistoryId ASC")
    private List<PaymentHistory> histories = new ArrayList<>();

    // 전달받은 값으로 결제 객체를 초기화한다.
    @Builder
    public Payment(
            Long paymentId,
            Reservation reservation,
            String paymentNo,
            PaymentMethod method,
            PaymentStatus status,
            Integer amount,
            Integer refundedAmount,
            Integer cancellationFeeAmount,
            Integer reservationFeeAmount,
            Integer deliveryFeeAmount,
            String idempotencyKey,
            CardPaymentInfo cardInfo,
            VirtualAccountPaymentInfo virtualAccountInfo,
            LocalDateTime requestedAt,
            LocalDateTime paidAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            PaymentSnapshot snapshot
    ) {

        this.paymentId = paymentId;
        this.reservation = reservation;
        this.paymentNo = paymentNo;
        this.method = method;
        this.status = status == null ? PaymentStatus.READY : status;
        this.refundedAmount = refundedAmount == null ? 0 : refundedAmount;
        this.cancellationFeeAmount = cancellationFeeAmount == null ? 0 : cancellationFeeAmount;
        this.idempotencyKey = idempotencyKey;
        this.cardInfo = cardInfo;
        this.virtualAccountInfo = virtualAccountInfo;
        this.requestedAt = requestedAt;
        this.paidAt = paidAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.snapshot =
                snapshot == null
                        ? PaymentSnapshot.builder()
                                .amount(amount)
                                .reservationFeeAmount(reservationFeeAmount)
                                .deliveryFeeAmount(deliveryFeeAmount)
                                .tickets(reservation == null ? List.of() : reservation.getTickets())
                                .discounts(List.of())
                                .build()
                        : snapshot;
        this.process = new PaymentProcess(initialStage(this.status));
        record(PaymentHistoryType.CREATED, null, null);
    }

    // 결제 수단에 맞는 초기 처리 단계를 결정한다.
    private static PaymentProcessStage initialStage(PaymentStatus status) {

        return switch (status) {
            case PAID, REFUNDED, PARTIALLY_REFUNDED -> PaymentProcessStage.LOCAL_COMPLETED;
            case WAITING_DEPOSIT -> PaymentProcessStage.WAITING_DEPOSIT;
            case FAILED -> PaymentProcessStage.FAILED;
            case EXPIRED -> PaymentProcessStage.EXPIRED;
            case CANCELLED -> PaymentProcessStage.CANCELLED;
            default -> PaymentProcessStage.READY_FOR_APPROVAL;
        };
    }

    // 금액 값을 반환한다.
    public Integer getAmount() {

        return snapshot.getAmount();
    }

    // 예매 수수료 금액 값을 반환한다.
    public Integer getReservationFeeAmount() {

        return snapshot.getReservationFeeAmount();
    }

    // 배송비 금액 값을 반환한다.
    public Integer getDeliveryFeeAmount() {

        return snapshot.getDeliveryFeeAmount();
    }

    // 처리 이력 목록 값을 반환한다.
    public List<PaymentHistory> getHistories() {

        return Collections.unmodifiableList(histories);
    }

    // 결제 처리에서 처리 이력을 기록한다.
    private void record(PaymentHistoryType type, PaymentStatus before, String reason) {

        histories.add(new PaymentHistory(this, type, before, reason));
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    public PaymentResponse toResponse() {

        return PaymentResponse.builder()
                .paymentId(this.paymentId)
                .reservationId(
                        this.reservation != null ? this.reservation.getReservationId() : null)
                .orderId(this.reservation != null ? this.reservation.getOrderId() : null)
                .paymentNo(this.paymentNo)
                .method(this.method)
                .status(this.status)
                .amount(getAmount())
                .refundedAmount(getRefundedAmount())
                .refundableAmount(getRefundableAmount())
                .cardTransactionId(getCardTransactionId())
                .cardCompany(getCardCompany())
                .maskedCardNumber(getMaskedCardNumber())
                .bankName(getBankName())
                .accountNumber(getAccountNumber())
                .depositorName(getDepositorName())
                .requestedAt(formatDateTime(this.requestedAt))
                .paidAt(formatDateTime(this.paidAt))
                .expiresAt(formatDateTime(this.expiresAt))
                .build();
    }

    // 결제를 가상계좌 입금 대기 상태로 변경한다.
    public void waitDeposit(String bankName, String accountNumber, LocalDateTime expiresAt) {

        PaymentStatus before = this.status;
        this.status = PaymentStatus.WAITING_DEPOSIT;

        if (this.virtualAccountInfo == null) {
            this.virtualAccountInfo = new VirtualAccountPaymentInfo();
        }

        this.virtualAccountInfo.issue(bankName, accountNumber);
        this.expiresAt = expiresAt;

        process.move(PaymentProcessStage.WAITING_DEPOSIT, null);
        record(PaymentHistoryType.ACCOUNT_ISSUED, before, null);
    }

    // 카드 결제를 계좌이체 방식으로 변경한다.
    public void switchToBankTransfer() {

        if (this.method != PaymentMethod.CREDIT_CARD || this.status != PaymentStatus.READY) {
            throw new IllegalStateException("무통장 결제로 변경할 수 없는 결제 상태입니다.");
        }
        this.method = PaymentMethod.BANK_TRANSFER;
        record(PaymentHistoryType.METHOD_CHANGED, status, null);
    }

    // 결제 처리에서 카드 결제 완료 결과를 반영한다.
    public void completeCard(
            String transactionId,
            CardCompany cardCompany,
            String maskedCardNumber,
            LocalDateTime paidAt
    ) {

        this.cardInfo = CardPaymentInfo.builder()
                .transactionId(transactionId)
                .cardCompany(cardCompany)
                .maskedCardNumber(maskedCardNumber)
                .build();
        complete(paidAt);
    }

    // 결제 처리에서 완료 상태로 변경하고 결과를 반영한다.
    public void complete(LocalDateTime paidAt) {

        PaymentStatus before = status;
        process.move(PaymentProcessStage.GATEWAY_APPROVED, null);
        record(PaymentHistoryType.GATEWAY_APPROVED, before, null);
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
        process.move(PaymentProcessStage.LOCAL_COMPLETED, null);
        record(PaymentHistoryType.COMPLETED, before, null);
    }

    // 결제 처리에서 가상계좌 입금 완료 결과를 반영한다.
    public void completeDeposit(String depositorName, LocalDateTime paidAt) {

        if (this.virtualAccountInfo == null) {
            this.virtualAccountInfo = new VirtualAccountPaymentInfo();
        }
        this.virtualAccountInfo.deposit(depositorName);
        complete(paidAt);
    }

    // 카드 승인 요청 상태를 반영한다.
    public void approvalRequested() {

        if (process.getStage() == PaymentProcessStage.APPROVAL_REQUESTED) return;
        process.move(PaymentProcessStage.APPROVAL_REQUESTED, null);
        record(PaymentHistoryType.APPROVAL_REQUESTED, status, null);
    }

    // 결제 처리에서 실패 상태로 변경한다.
    public void fail() {

        fail(null);
    }

    // 결제 처리에서 실패 상태로 변경한다.
    public void fail(String reason) {

        if (status == PaymentStatus.FAILED) return;
        PaymentStatus before = status;
        this.status = PaymentStatus.FAILED;
        process.move(PaymentProcessStage.FAILED, reason);
        record(PaymentHistoryType.FAILED, before, reason);
    }

    // 결제를 취소 상태로 변경한다.
    public void cancel() {

        if (status == PaymentStatus.CANCELLED) return;
        PaymentStatus before = status;
        this.status = PaymentStatus.CANCELLED;
        process.move(PaymentProcessStage.CANCELLED, null);
        record(PaymentHistoryType.CANCELLED, before, null);
    }

    // 결제를 만료 상태로 변경한다.
    public void expire() {

        if (status == PaymentStatus.EXPIRED) return;
        PaymentStatus before = status;
        this.status = PaymentStatus.EXPIRED;
        process.move(PaymentProcessStage.EXPIRED, null);
        record(PaymentHistoryType.EXPIRED, before, null);
    }

    // 결제 처리에서 환불 요청을 처리하고 결과를 반영한다.
    public void refund() {

        PaymentStatus before = status;
        this.refundedAmount = getRefundedAmount() + getRefundableAmount();
        this.status = PaymentStatus.REFUNDED;
        record(PaymentHistoryType.REFUNDED, before, null);
    }

    // 부분 환불 결과와 남은 결제 금액을 반영한다.
    public void partialRefund(Integer refundAmount) {

        PaymentStatus before = status;
        validateRefundAmount(refundAmount);
        this.refundedAmount = getRefundedAmount() + refundAmount;
        updateRefundStatus();
        record(PaymentHistoryType.REFUNDED, before, null);
    }

    // 취소 수수료를 결제 정보에 반영한다.
    public void applyCancellationFee(Integer cancellationFeeAmount) {

        PaymentStatus before = status;
        if (cancellationFeeAmount == null || cancellationFeeAmount < 0) {
            throw new IllegalArgumentException("취소 수수료는 0 이상이어야 합니다.");
        }

        if (cancellationFeeAmount > getRefundableAmount()) {
            throw new IllegalArgumentException("취소 수수료가 남은 결제 금액을 초과했습니다.");
        }
        this.cancellationFeeAmount = getCancellationFeeAmount() + cancellationFeeAmount;
        updateRefundStatus();
        record(PaymentHistoryType.CANCELLATION_FEE_APPLIED, before, null);
    }

    // 취소 금액과 결제 상태를 반영한다.
    public void applyCancellation(Integer refundAmount, Integer cancellationFeeAmount) {

        PaymentStatus before = status;
        int fee = cancellationFeeAmount != null ? cancellationFeeAmount : 0;
        if (refundAmount == null || refundAmount < 0 || refundAmount + fee <= 0) {
            throw new IllegalArgumentException("환불 금액과 취소 수수료의 합은 0보다 커야 합니다.");
        }

        if (refundAmount + fee > getRefundableAmount()) {
            throw new IllegalArgumentException("환불 금액과 취소 수수료가 남은 결제 금액을 초과했습니다.");
        }
        this.refundedAmount = getRefundedAmount() + refundAmount;
        this.cancellationFeeAmount = getCancellationFeeAmount() + fee;
        updateRefundStatus();
        record(PaymentHistoryType.REFUNDED, before, null);
    }

    // 결제 처리에서 처리 준비 상태로 구성한다.
    public void ready() {

        PaymentStatus before = status;
        this.status = PaymentStatus.READY;
        this.refundedAmount = 0;
        this.cancellationFeeAmount = 0;
        this.paidAt = null;
        this.expiresAt = null;
        process.move(PaymentProcessStage.READY_FOR_APPROVAL, null);
        record(PaymentHistoryType.RETRY_REQUESTED, before, null);
    }

    // 날짜와 시각을 응답 표시 형식으로 변환한다.
    private String formatDateTime(LocalDateTime dateTime) {

        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }

    // 카드 승인 거래 식별자 값을 반환한다.
    public String getCardTransactionId() {

        return cardInfo != null ? cardInfo.getTransactionId() : null;
    }

    // 카드사 값을 반환한다.
    public CardCompany getCardCompany() {

        return cardInfo != null ? cardInfo.getCardCompany() : null;
    }

    // 마스킹한 카드 번호 값을 반환한다.
    public String getMaskedCardNumber() {

        return cardInfo != null ? cardInfo.getMaskedCardNumber() : null;
    }

    // 은행 이름 값을 반환한다.
    public String getBankName() {

        return virtualAccountInfo != null ? virtualAccountInfo.getBankName() : null;
    }

    // 계좌 번호 값을 반환한다.
    public String getAccountNumber() {

        return virtualAccountInfo != null ? virtualAccountInfo.getAccountNumber() : null;
    }

    // 입금자 이름 값을 반환한다.
    public String getDepositorName() {

        return virtualAccountInfo != null ? virtualAccountInfo.getDepositorName() : null;
    }

    // 누적 환불 금액 값을 반환한다.
    public Integer getRefundedAmount() {

        return refundedAmount != null ? refundedAmount : 0;
    }

    // 환불 가능 잔액 값을 반환한다.
    public Integer getRefundableAmount() {

        return getAmount()
                - getRefundedAmount()
                - getCancellationFeeAmount()
                - getReservationFeeAmount();
    }

    // 취소 수수료 금액 값을 반환한다.
    public Integer getCancellationFeeAmount() {

        return cancellationFeeAmount != null ? cancellationFeeAmount : 0;
    }

    // 누적 환불 금액에 따라 환불 상태를 갱신한다.
    private void updateRefundStatus() {

        this.status = getRefundableAmount() == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    // 환불 요청 금액이 환불 가능 범위에 있는지 검증한다.
    private void validateRefundAmount(Integer refundAmount) {

        if (refundAmount == null || refundAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }

        if (refundAmount > getRefundableAmount()) {
            throw new IllegalArgumentException("환불 금액이 남은 결제 금액을 초과했습니다.");
        }
    }
}
