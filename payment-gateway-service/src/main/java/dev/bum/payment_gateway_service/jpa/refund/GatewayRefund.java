package dev.bum.payment_gateway_service.jpa.refund;

import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 최초 요청은 별도 트랜잭션으로 보존하고, 성공 상태는 실제 환불 변경과 함께 저장한다. */
@Entity
@Table(name = "gateway_refunds", indexes = @Index(name = "idx_gateway_refunds_payment_no", columnList = "payment_no"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayRefund implements Persistable<Long> {
    @Id
    @Column(name = "payment_refund_process_id")
    private Long paymentRefundProcessId;
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;
    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private GatewayRefundStatus status = GatewayRefundStatus.REQUESTED;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_bank_company", length = 30)
    private BankCompany refundBankCompany;
    @Column(name = "refund_account_number", length = 100)
    private String refundAccountNumber;
    @Column(name = "refund_account_holder", length = 100)
    private String refundAccountHolder;
    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", length = 40)
    private CardPaymentHistoryStatus cardStatus;
    @Column(nullable = false, length = 500)
    private String message;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Transient
    @Builder.Default
    private boolean newRecord = true;

    @Override
    public Long getId() { return paymentRefundProcessId; }

    // 직접 지정한 ID도 신규 저장 시 persist를 사용해 다른 요청의 기록을 덮어쓰지 않는다.
    @Override
    public boolean isNew() { return newRecord; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newRecord = false; }

    public static GatewayRefund card(GatewayCardPaymentRefundRequest request, GatewayCardPaymentRefundResponse response) {
        GatewayRefund refund = requested(request);
        refund.succeeded(response);
        return refund;
    }

    public static GatewayRefund requested(GatewayCardPaymentRefundRequest request) {
        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.CREDIT_CARD).refundAmount(request.getRefundAmount())
                .transactionId(request.getTransactionId()).message("환불 요청을 접수했습니다.").build();
    }

    public static GatewayRefund virtualAccount(GatewayVirtualAccountRefundRequest request, GatewayVirtualAccountRefundResponse response) {
        GatewayRefund refund = requested(request);
        refund.succeeded(response);
        return refund;
    }

    public static GatewayRefund requested(GatewayVirtualAccountRefundRequest request) {
        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.BANK_TRANSFER).refundAmount(request.getRefundAmount())
                .refundBankCompany(request.getRefundBankCompany()).refundAccountNumber(request.getRefundAccountNumber())
                .refundAccountHolder(request.getRefundAccountHolder()).message("환불 요청을 접수했습니다.").build();
    }

    public GatewayCardPaymentRefundResponse replay(GatewayCardPaymentRefundRequest request) {
        validateRequest(request);
        requireSucceeded();
        return GatewayCardPaymentRefundResponse.builder().paymentNo(paymentNo).transactionId(transactionId)
                .refundedAmount(refundAmount).status(cardStatus).message(message).build();
    }

    public GatewayVirtualAccountRefundResponse replay(GatewayVirtualAccountRefundRequest request) {
        validateRequest(request);
        requireSucceeded();
        return GatewayVirtualAccountRefundResponse.builder().paymentNo(paymentNo).refundBankCompany(refundBankCompany)
                .refundAccountNumber(refundAccountNumber).refundAccountHolder(refundAccountHolder)
                .refundedAmount(refundAmount).message(message).build();
    }

    public void validateRequest(GatewayCardPaymentRefundRequest request) {
        require(!isClosed());
        validate(request.getPaymentNo(), PaymentMethod.CREDIT_CARD, request.getRefundAmount());
        require(Objects.equals(transactionId, request.getTransactionId()));
    }

    public void validateRequest(GatewayVirtualAccountRefundRequest request) {
        require(!isClosed());
        validate(request.getPaymentNo(), PaymentMethod.BANK_TRANSFER, request.getRefundAmount());
        require(refundBankCompany == request.getRefundBankCompany()
                && Objects.equals(refundAccountNumber, request.getRefundAccountNumber())
                && Objects.equals(refundAccountHolder, request.getRefundAccountHolder()));
    }

    public static GatewayRefund closed(GatewayRefundResolutionRequest request) {
        return GatewayRefund.builder()
                .paymentRefundProcessId(request.paymentRefundProcessId())
                .paymentNo(request.paymentNo()).method(request.method()).refundAmount(request.refundAmount())
                .status(GatewayRefundStatus.CLOSED).message("요청 변경으로 실행하지 않은 환불을 종료했습니다.").build();
    }

    public boolean isSucceeded() { return status == GatewayRefundStatus.SUCCEEDED; }

    public boolean isClosed() { return status == GatewayRefundStatus.CLOSED; }

    public void retryRequested() {
        requireExecutable();
        status = GatewayRefundStatus.REQUESTED;
        failureReason = null;
        message = "환불 요청을 처리 중입니다.";
    }

    public void succeeded(GatewayCardPaymentRefundResponse response) {
        requireExecutable();
        cardStatus = response.getStatus();
        succeed(response.getMessage());
    }

    public void succeeded(GatewayVirtualAccountRefundResponse response) {
        requireExecutable();
        succeed(response.getMessage());
    }

    private void succeed(String resultMessage) {
        status = GatewayRefundStatus.SUCCEEDED;
        failureReason = null;
        message = resultMessage;
    }

    public void failed(String reason) {
        // 성공·종료 결과는 지연된 실패 기록으로 되돌리지 않는다.
        if (isSucceeded() || isClosed()) return;
        status = GatewayRefundStatus.FAILED;
        failureReason = reason == null ? "환불 트랜잭션이 롤백되었습니다." : reason.substring(0, Math.min(1000, reason.length()));
        message = "환불 처리에 실패했습니다.";
    }

    public void close() {
        if (isSucceeded()) throw new IllegalStateException("성공한 환불은 종료할 수 없습니다.");
        status = GatewayRefundStatus.CLOSED;
        message = "요청 변경으로 실행하지 않은 환불을 종료했습니다.";
    }

    private void requireExecutable() {
        if (isSucceeded() || isClosed()) throw new IllegalStateException("이미 완료되거나 종료된 환불 요청입니다.");
    }

    private void requireSucceeded() {
        if (!isSucceeded()) throw new IllegalStateException("성공하지 않은 환불 결과는 재사용할 수 없습니다.");
    }

    public void validateResolution(GatewayRefundResolutionRequest request) {
        validate(request.paymentNo(), request.method(), request.refundAmount());
    }

    private void validate(String requestedPaymentNo, PaymentMethod requestedMethod, BigDecimal requestedAmount) {
        require(Objects.equals(paymentNo, requestedPaymentNo) && method == requestedMethod
                && requestedAmount != null && refundAmount.compareTo(requestedAmount) == 0);
    }

    private void require(boolean matches) {
        if (!matches) throw new IllegalArgumentException("동일한 환불 프로세스 ID로 다른 환불 정보를 요청할 수 없습니다.");
    }
}
