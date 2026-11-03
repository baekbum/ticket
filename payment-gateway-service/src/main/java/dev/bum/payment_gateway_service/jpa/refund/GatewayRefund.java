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

    // 결제 환불 처리 식별자.
    @Id
    @Column(name = "payment_refund_process_id")
    private Long paymentRefundProcessId;

    // 결제 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 결제 수단.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    // 환불 금액.
    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount;

    // 환불 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private GatewayRefundStatus status = GatewayRefundStatus.REQUESTED;

    // 실패 사유.
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    // 동시성 제어 버전.
    @Version
    @Column(nullable = false)
    private Long version;

    // 결제 거래 식별자.
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    // 환불받을 은행.
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_bank_company", length = 30)
    private BankCompany refundBankCompany;

    // 환불 계좌 번호.
    @Column(name = "refund_account_number", length = 100)
    private String refundAccountNumber;

    // 환불 계좌 예금주.
    @Column(name = "refund_account_holder", length = 100)
    private String refundAccountHolder;

    // 카드 결제 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", length = 40)
    private CardPaymentHistoryStatus cardStatus;

    // 처리 결과 메시지.
    @Column(nullable = false, length = 500)
    private String message;

    // 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 신규 영속 객체 여부.
    @Transient
    @Builder.Default
    private boolean newRecord = true;

    // 고유 식별자 값을 반환한다.
    @Override
    public Long getId() {

        return paymentRefundProcessId; }

    // 아직 저장하지 않은 영속 객체인지 확인한다.
    // 직접 지정한 ID도 신규 저장 시 persist를 사용해 다른 요청의 기록을 덮어쓰지 않는다.
    @Override
    public boolean isNew() {

        return newRecord; }

    // 영속 객체의 저장 완료 상태를 표시한다.
    @PostLoad
    @PostPersist
    private void markPersisted() {

        newRecord = false; }

    // 카드 결제에 필요한 정보를 구성한다.
    public static GatewayRefund card(
            GatewayCardPaymentRefundRequest request,
            GatewayCardPaymentRefundResponse response
    ) {

        GatewayRefund refund = requested(request);
        refund.succeeded(response);
        return refund;
    }

    // 환불 처리에서 요청 상태로 구성한다.
    public static GatewayRefund requested(GatewayCardPaymentRefundRequest request) {

        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.CREDIT_CARD).refundAmount(request.getRefundAmount())
                .transactionId(request.getTransactionId()).message("환불 요청을 접수했습니다.").build();
    }

    // 가상계좌 결제에 필요한 정보를 구성한다.
    public static GatewayRefund virtualAccount(
            GatewayVirtualAccountRefundRequest request,
            GatewayVirtualAccountRefundResponse response
    ) {

        GatewayRefund refund = requested(request);
        refund.succeeded(response);
        return refund;
    }

    // 환불 처리에서 요청 상태로 구성한다.
    public static GatewayRefund requested(GatewayVirtualAccountRefundRequest request) {

        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.BANK_TRANSFER).refundAmount(request.getRefundAmount())
                .refundBankCompany(request.getRefundBankCompany()).refundAccountNumber(request.getRefundAccountNumber())
                .refundAccountHolder(request.getRefundAccountHolder()).message("환불 요청을 접수했습니다.").build();
    }

    // 실패 메시지를 재발행한다.
    public GatewayCardPaymentRefundResponse replay(GatewayCardPaymentRefundRequest request) {

        validateRequest(request);
        requireSucceeded();
        return GatewayCardPaymentRefundResponse.builder().paymentNo(paymentNo).transactionId(transactionId)
                .refundedAmount(refundAmount).status(cardStatus).message(message).build();
    }

    // 실패 메시지를 재발행한다.
    public GatewayVirtualAccountRefundResponse replay(GatewayVirtualAccountRefundRequest request) {

        validateRequest(request);
        requireSucceeded();
        return GatewayVirtualAccountRefundResponse.builder().paymentNo(paymentNo).refundBankCompany(refundBankCompany)
                .refundAccountNumber(refundAccountNumber).refundAccountHolder(refundAccountHolder)
                .refundedAmount(refundAmount).message(message).build();
    }

    // 요청 값과 처리 가능 조건을 검증한다.
    public void validateRequest(GatewayCardPaymentRefundRequest request) {

        require(!isClosed());
        validate(request.getPaymentNo(), PaymentMethod.CREDIT_CARD, request.getRefundAmount());
        require(Objects.equals(transactionId, request.getTransactionId()));
    }

    // 요청 값과 처리 가능 조건을 검증한다.
    public void validateRequest(GatewayVirtualAccountRefundRequest request) {

        require(!isClosed());
        validate(request.getPaymentNo(), PaymentMethod.BANK_TRANSFER, request.getRefundAmount());
        require(refundBankCompany == request.getRefundBankCompany()
                && Objects.equals(refundAccountNumber, request.getRefundAccountNumber())
                && Objects.equals(refundAccountHolder, request.getRefundAccountHolder()));
    }

    // 종료된 요청 결과를 구성한다.
    public static GatewayRefund closed(GatewayRefundResolutionRequest request) {

        return GatewayRefund.builder()
                .paymentRefundProcessId(request.paymentRefundProcessId())
                .paymentNo(request.paymentNo()).method(request.method()).refundAmount(request.refundAmount())
                .status(GatewayRefundStatus.CLOSED).message("요청 변경으로 실행하지 않은 환불을 종료했습니다.").build();
    }

    // 처리가 성공했는지 확인한다.
    public boolean isSucceeded() {

        return status == GatewayRefundStatus.SUCCEEDED; }

    // 처리가 종료되었는지 확인한다.
    public boolean isClosed() {

        return status == GatewayRefundStatus.CLOSED; }

    // 재시도 요청 상태와 이력을 반영한다.
    public void retryRequested() {

        requireExecutable();
        status = GatewayRefundStatus.REQUESTED;
        failureReason = null;
        message = "환불 요청을 처리 중입니다.";
    }

    // 환불 처리에서 성공 상태로 구성한다.
    public void succeeded(GatewayCardPaymentRefundResponse response) {

        requireExecutable();
        cardStatus = response.getStatus();
        succeed(response.getMessage());
    }

    // 환불 처리에서 성공 상태로 구성한다.
    public void succeeded(GatewayVirtualAccountRefundResponse response) {

        requireExecutable();
        succeed(response.getMessage());
    }

    // 처리를 성공 상태로 변경한다.
    private void succeed(String resultMessage) {

        status = GatewayRefundStatus.SUCCEEDED;
        failureReason = null;
        message = resultMessage;
    }

    // 환불 처리에서 실패 사유와 상태를 반영한다.
    public void failed(String reason) {

        // 성공·종료 결과는 지연된 실패 기록으로 되돌리지 않는다.
        if (isSucceeded() || isClosed()) return;
        status = GatewayRefundStatus.FAILED;
        failureReason = reason == null ? "환불 트랜잭션이 롤백되었습니다." : reason.substring(0, Math.min(1000, reason.length()));
        message = "환불 처리에 실패했습니다.";
    }

    // 처리를 종료 상태로 변경한다.
    public void close() {

        if (isSucceeded()) throw new IllegalStateException("성공한 환불은 종료할 수 없습니다.");
        status = GatewayRefundStatus.CLOSED;
        message = "요청 변경으로 실행하지 않은 환불을 종료했습니다.";
    }

    // 현재 요청을 실행할 수 있는 상태인지 검증한다.
    private void requireExecutable() {

        if (isSucceeded() || isClosed()) throw new IllegalStateException("이미 완료되거나 종료된 환불 요청입니다.");
    }

    // 현재 요청이 성공한 상태인지 검증한다.
    private void requireSucceeded() {

        if (!isSucceeded()) throw new IllegalStateException("성공하지 않은 환불 결과는 재사용할 수 없습니다.");
    }

    // 수동 해결 요청이 현재 상태에 유효한지 검증한다.
    public void validateResolution(GatewayRefundResolutionRequest request) {

        validate(request.paymentNo(), request.method(), request.refundAmount());
    }

    // 환불 처리에서 요청 값과 현재 상태가 유효한지 검증한다.
    private void validate(String requestedPaymentNo, PaymentMethod requestedMethod, BigDecimal requestedAmount) {

        require(Objects.equals(paymentNo, requestedPaymentNo) && method == requestedMethod
                && requestedAmount != null && refundAmount.compareTo(requestedAmount) == 0);
    }

    // 처리에 필요한 대상이 존재하는지 확인한다.
    private void require(boolean matches) {

        if (!matches) throw new IllegalArgumentException("동일한 환불 프로세스 ID로 다른 환불 정보를 요청할 수 없습니다.");
    }
}
