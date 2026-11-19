package dev.bum.ticket_service.jpa.payment.virtualAccount;

import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.feign.paymentgateway.GatewayAccountCancelRequest;
import java.util.Objects;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_account_cancel_outbox", indexes =
        @Index(name = "idx_va_cancel_outbox_ready", columnList = "status,next_attempt_at,outbox_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_va_cancel_outbox_payment_no", columnNames = "payment_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccountCancelOutbox {

    // 발행 대기 이벤트 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 결제 번호.
    @Column(name = "payment_no", nullable = false, updatable = false, length = 60)
    private String paymentNo;

    // 은행 이름.
    @Column(name = "bank_name", nullable = false, updatable = false, length = 50)
    private String bankName;

    // 계좌 번호.
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    // 신규 경로의 PG 식별자. 발급 응답 유실 중 취소하면 발급 복구 후 한 번 연결한다.
    @Column(name = "pg_payment_id", length = 44)
    private String pgPaymentId;

    // 최초 예매 소유자. 기존 Outbox는 null이다.
    @Column(name = "buyer_reference", length = 100, updatable = false)
    private String buyerReference;

    // 최초 선택 은행. null이면 기존 취소 계약을 사용한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", length = 30, updatable = false)
    private BankCompany bankCompany;

    // 요청 전달과 구분하는 PG 처리 결과: PROCESSING/CANCELLED/REJECTED.
    @Column(name = "gateway_result", length = 30)
    private String gatewayResult;

    // 은행의 최종 거절 또는 취소 사유.
    @Column(name = "gateway_reason", length = 100)
    private String gatewayReason;

    // 금액.
    @Column(nullable = false, updatable = false)
    private Integer amount;

    // 가상계좌 취소 Outbox 이벤트 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VirtualAccountCancelOutboxStatus status;

    // 재시도 횟수.
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 다음 재시도 예정 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 짧은 선점 트랜잭션의 소유 시도 번호. 만료된 작업자의 늦은 응답은 반영하지 않는다.
    @Column(name = "delivery_attempt", nullable = false)
    private long deliveryAttempt;

    // HTTP 동안 DB 잠금을 잡지 않고, 프로세스 중단 후에는 다시 선점할 수 있게 하는 기한.
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    // 마지막 오류 유형.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 생성 시각.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 전달 또는 배송 완료 시각.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 가상계좌 취소 Outbox 이벤트 처리에서 처리 대기 상태로 구성한다.
    public static VirtualAccountCancelOutbox pending(Payment payment) {

        if (payment.getGatewayBankCompany() == null) {
            throw new IllegalArgumentException("신규 PG 가상계좌의 취소 작업만 등록할 수 있습니다.");
        }

        VirtualAccountCancelOutbox outbox = new VirtualAccountCancelOutbox();
        outbox.paymentNo = payment.getPaymentNo();
        outbox.pgPaymentId = payment.getPgPaymentId();
        outbox.bankCompany = payment.getGatewayBankCompany();
        outbox.buyerReference = payment.getReservation().getUserId();
        outbox.bankName = payment.getBankName();
        outbox.accountNumber = payment.getAccountNumber();
        outbox.amount = payment.getAmount();
        outbox.status = VirtualAccountCancelOutboxStatus.PENDING;
        outbox.createdAt = LocalDateTime.now();
        outbox.nextAttemptAt = outbox.createdAt;
        return outbox;
    }

    // 짧은 트랜잭션에서 전송을 선점한다. 임대 만료 후에도 원거래 멱등 키는 동일하다.
    public boolean claim(LocalDateTime now) {

        if (status == VirtualAccountCancelOutboxStatus.DELIVERED || nextAttemptAt.isAfter(now)
                || (leaseUntil != null && leaseUntil.isAfter(now))) {
            return false;
        }

        deliveryAttempt = Math.incrementExact(deliveryAttempt);
        leaseUntil = now.plusSeconds(60);
        return true;
    }

    // 원거래 잠금 아래 발급 복구 결과를 연결한다. 최초 계좌·금액·소유자가 다른 재요청은 거절한다.
    public void bindGateway(Payment payment) {

        if (bankCompany == null || bankCompany != payment.getGatewayBankCompany()
                || !paymentNo.equals(payment.getPaymentNo()) || !amount.equals(payment.getAmount())
                || !buyerReference.equals(payment.getReservation().getUserId())
                || !bankName.equals(payment.getBankName())
                || (pgPaymentId != null && !pgPaymentId.equals(payment.getPgPaymentId()))
                || (accountNumber != null && !accountNumber.equals(payment.getAccountNumber()))) {
            throw new IllegalStateException("취소 Outbox와 최초 PG 원거래가 일치하지 않습니다.");
        }

        pgPaymentId = payment.getPgPaymentId();
        accountNumber = payment.getAccountNumber();
    }

    // 신규 취소 요청은 저장한 거래·사용자·계좌로만 구성한다.
    public GatewayAccountCancelRequest toGatewayRequest(long merchantId) {

        if (bankCompany == null || pgPaymentId == null || accountNumber == null) {
            throw new IllegalStateException("PG 계좌 발급 복구가 완료되지 않았습니다.");
        }

        return new GatewayAccountCancelRequest(merchantId, buyerReference, BigDecimal.valueOf(amount),
                bankCompany, accountNumber);
    }

    // 접수와 최종 금융 결과를 구분한다. 늦은 접수 응답이나 실패가 최종 통보를 덮어쓰지 않는다.
    public void gatewayResult(String result, String reason) {

        if (gatewayResult != null && !"PROCESSING".equals(gatewayResult)) {
            if (!"PROCESSING".equals(result)
                    && (!gatewayResult.equals(result) || !Objects.equals(gatewayReason, reason))) {
                throw new IllegalStateException("PG 취소의 최초 최종 결과와 일치하지 않습니다.");
            }

            return;
        }

        gatewayResult = result;
        gatewayReason = reason;
    }

    // 가상계좌 취소 Outbox 이벤트 처리에서 전달 완료 상태를 반영한다.
    public void delivered(LocalDateTime now) {

        if (status == VirtualAccountCancelOutboxStatus.DELIVERED) return;

        status = VirtualAccountCancelOutboxStatus.DELIVERED;
        deliveredAt = now;
        leaseUntil = null;
        lastErrorType = null;
    }

    // 가상계좌 취소 Outbox 이벤트 처리에서 실패 사유와 상태를 반영한다.
    public void failed(String errorType, LocalDateTime nextAttemptAt) {

        if (status == VirtualAccountCancelOutboxStatus.DELIVERED) return;

        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
        leaseUntil = null;
    }
}
