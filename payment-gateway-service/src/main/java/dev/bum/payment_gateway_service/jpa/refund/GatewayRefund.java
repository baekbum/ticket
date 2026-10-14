package dev.bum.payment_gateway_service.jpa.refund;

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
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 환불 성공 기록과 최초 응답을 결제 정보 변경과 같은 트랜잭션으로 커밋한다. */
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
    @Transient
    @Builder.Default
    private boolean newRecord = true;

    @Override
    public Long getId() { return paymentRefundProcessId; }

    // 환불 기록은 새로 저장한 뒤 변경하지 않는다. 직접 지정한 ID도 persist로 저장하여 기존 기록을 덮어쓰지 않게 한다.
    @Override
    public boolean isNew() { return newRecord; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newRecord = false; }

    public static GatewayRefund card(GatewayCardPaymentRefundRequest request, GatewayCardPaymentRefundResponse response) {
        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.CREDIT_CARD).refundAmount(request.getRefundAmount())
                .transactionId(request.getTransactionId()).cardStatus(response.getStatus()).message(response.getMessage()).build();
    }

    public static GatewayRefund virtualAccount(GatewayVirtualAccountRefundRequest request, GatewayVirtualAccountRefundResponse response) {
        return GatewayRefund.builder().paymentRefundProcessId(request.getPaymentRefundProcessId())
                .paymentNo(request.getPaymentNo()).method(PaymentMethod.BANK_TRANSFER).refundAmount(request.getRefundAmount())
                .refundBankCompany(request.getRefundBankCompany()).refundAccountNumber(request.getRefundAccountNumber())
                .refundAccountHolder(request.getRefundAccountHolder()).message(response.getMessage()).build();
    }

    public GatewayCardPaymentRefundResponse replay(GatewayCardPaymentRefundRequest request) {
        validate(request.getPaymentNo(), PaymentMethod.CREDIT_CARD, request.getRefundAmount());
        require(Objects.equals(transactionId, request.getTransactionId()));
        return GatewayCardPaymentRefundResponse.builder().paymentNo(paymentNo).transactionId(transactionId)
                .refundedAmount(refundAmount).status(cardStatus).message(message).build();
    }

    public GatewayVirtualAccountRefundResponse replay(GatewayVirtualAccountRefundRequest request) {
        validate(request.getPaymentNo(), PaymentMethod.BANK_TRANSFER, request.getRefundAmount());
        require(refundBankCompany == request.getRefundBankCompany()
                && Objects.equals(refundAccountNumber, request.getRefundAccountNumber())
                && Objects.equals(refundAccountHolder, request.getRefundAccountHolder()));
        return GatewayVirtualAccountRefundResponse.builder().paymentNo(paymentNo).refundBankCompany(refundBankCompany)
                .refundAccountNumber(refundAccountNumber).refundAccountHolder(refundAccountHolder)
                .refundedAmount(refundAmount).message(message).build();
    }

    private void validate(String requestedPaymentNo, PaymentMethod requestedMethod, BigDecimal requestedAmount) {
        require(Objects.equals(paymentNo, requestedPaymentNo) && method == requestedMethod
                && requestedAmount != null && refundAmount.compareTo(requestedAmount) == 0);
    }

    private void require(boolean matches) {
        if (!matches) throw new IllegalArgumentException("동일한 환불 프로세스 ID로 다른 환불 정보를 요청할 수 없습니다.");
    }
}
