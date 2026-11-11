package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.merchant.Merchant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "gateway_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gateway_payments_merchant_no", columnNames = {"merchant_id", "merchant_payment_no"}),
        @UniqueConstraint(name = "uk_gateway_payments_id_merchant", columnNames = {"pg_payment_id", "merchant_id"})
}, indexes = {
        @Index(name = "idx_gateway_payments_merchant_buyer", columnList = "merchant_id,buyer_reference,created_at,pg_payment_id"),
        @Index(name = "idx_gateway_payments_expiration", columnList = "status,expires_at,pg_payment_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayPayment {

    // PG 식별자 앞의 발급일은 서버 실행 환경과 무관하게 한국 날짜로 결정한다.
    private static final ZoneId PAYMENT_ID_ZONE = ZoneId.of("Asia/Seoul");

    // 최초 저장 시 yyyyMMdd + UUID로 발급하는 44자 문자열. 가맹점 결제번호나 카드 거래번호와 다르다.
    @Id
    @Column(name = "pg_payment_id", length = 44, updatable = false)
    private String pgPaymentId;

    // 인증된 소유 가맹점.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_gateway_payments_merchant"))
    private Merchant merchant;

    // 가맹점 내부 결제번호. 단독 전역 유니크로 사용하지 않는다.
    @Column(name = "merchant_payment_no", nullable = false, length = 60, updatable = false)
    private String merchantPaymentNo;

    // 가맹점이 확인한 예매 소유자.
    @Column(name = "buyer_reference", nullable = false, length = 50, updatable = false)
    private String buyerReference;

    // 금융 도메인 분기의 기준이 되는 저장된 수단.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    // 승인 시 확정할 카드사.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", length = 30)
    private CardCompany cardCompany;

    // 계좌 결제 요청에서 확정한 은행.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", length = 30)
    private BankCompany bankCompany;

    // 변경하지 않는 최초 금액.
    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    // 실제 성공 환불 누적액.
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount;

    // 금융 처리 결과를 나타내는 PG 상태. 통보 상태와 분리한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GatewayPaymentStatus status;

    // 최초 준비 내용의 불변 해시.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "creation_request_hash", nullable = false, length = 64, updatable = false)
    private String creationRequestHash;

    // 명시적 결제수단 변경 때 증가시킬 버전.
    @Column(name = "method_revision", nullable = false)
    private int methodRevision;

    // 현재 결제수단의 승인 또는 입금 기한.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 최초 금융 완료 사실.
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // PG 취소 확정 시각. 실제 환불과 다르다.
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // 지연된 상태 기록이 최신 결과를 덮어쓰지 않게 하는 버전.
    @Version
    @Column(nullable = false)
    private long version;

    // 최초 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 금융 실행 없이 최초 PG 거래를 준비한다.
    public static GatewayPayment prepare(Merchant merchant, GatewayPaymentTerms terms) {

        GatewayPayment payment = new GatewayPayment();
        payment.merchant = merchant;
        payment.merchantPaymentNo = terms.paymentNo();
        payment.buyerReference = terms.buyerReference();
        payment.paymentMethod = terms.paymentMethod();
        payment.bankCompany = terms.bankCompany();
        payment.amount = terms.amount();
        payment.refundedAmount = BigDecimal.ZERO.setScale(2);
        payment.status = GatewayPaymentStatus.READY;
        payment.creationRequestHash = terms.fingerprint();
        payment.expiresAt = terms.expiresAt();

        return payment;
    }

    // 은행의 실제 발급 성공을 확인한 뒤 입금 대기 상태로 전환한다.
    public void waitForDeposit() {

        if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != GatewayPaymentStatus.READY) {
            throw new IllegalStateException("가상계좌 발급 결과를 반영할 수 없는 PG 상태입니다.");
        }

        status = GatewayPaymentStatus.WAITING_DEPOSIT;
    }

    // 카드사의 원승인 성공을 확인한 뒤 금융 완료 상태로 전환한다.
    public void approveCard(CardCompany company, LocalDateTime approvedAt) {

        if (paymentMethod != PaymentMethod.CREDIT_CARD || status != GatewayPaymentStatus.READY
                || company == null || approvedAt == null) {
            throw new IllegalStateException("카드 승인 결과를 반영할 수 없는 PG 상태입니다.");
        }

        cardCompany = company;
        paidAt = approvedAt;
        status = GatewayPaymentStatus.PAID;
    }

    // 실제 카드사 승인 보상이 완료된 뒤에만 PG 결제를 취소한다.
    public void cancelCardApproval() {

        if (paymentMethod != PaymentMethod.CREDIT_CARD || status != GatewayPaymentStatus.PAID) {
            throw new IllegalStateException("카드 승인 보상을 반영할 수 없는 PG 상태입니다.");
        }

        status = GatewayPaymentStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    // 은행의 최초 입금 시각을 보존한다. 이미 취소된 거래를 입금으로 되살리지 않는다.
    public void receiveDeposit(LocalDateTime depositedAt) {

        if (paymentMethod != PaymentMethod.BANK_TRANSFER || depositedAt == null
                || (status != GatewayPaymentStatus.WAITING_DEPOSIT && status != GatewayPaymentStatus.CANCELLED)) {
            throw new IllegalStateException("은행 입금 사실을 반영할 수 없는 PG 상태입니다.");
        }

        paidAt = depositedAt;

        if (status != GatewayPaymentStatus.CANCELLED) {
            status = GatewayPaymentStatus.PAID;
        }
    }

    // 은행의 취소 커밋을 확인한 뒤 입금 전 PG 거래를 취소한다. 같은 거래 재요청은 최초 시각을 유지한다.
    public void cancelVirtualAccount() {

        if (paymentMethod != PaymentMethod.BANK_TRANSFER || paidAt != null
                || (status != GatewayPaymentStatus.WAITING_DEPOSIT && status != GatewayPaymentStatus.CANCELLED)) {
            throw new IllegalStateException("입금 전 취소를 반영할 수 없는 PG 상태입니다.");
        }

        if (status != GatewayPaymentStatus.CANCELLED) {
            status = GatewayPaymentStatus.CANCELLED;
            cancelledAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    // 은행이 확정한 취소만 반영한다. 입금 여부 판단·입금 이력 생성·자금 반환은 수행하지 않는다.
    public void confirmBankCancellation() {

        if (paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalStateException("은행 취소 결과를 반영할 수 없는 결제수단입니다.");
        }

        if (status != GatewayPaymentStatus.CANCELLED) {
            status = GatewayPaymentStatus.CANCELLED;
        }

        if (cancelledAt == null) {
            cancelledAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    // 취소된 예매의 입금 통보를 정리한다. 결제 취소는 실제 자금 반환을 뜻하지 않는다.
    public void cancelDepositedPayment() {

        if (paymentMethod != PaymentMethod.BANK_TRANSFER || paidAt == null
                || (status != GatewayPaymentStatus.PAID && status != GatewayPaymentStatus.CANCELLED)) {
            throw new IllegalStateException("입금된 PG 거래를 취소할 수 없습니다.");
        }

        if (status != GatewayPaymentStatus.CANCELLED) {
            status = GatewayPaymentStatus.CANCELLED;
            cancelledAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    // 같은 키의 다른 요청을 기존 거래 성공으로 잘못 재응답하지 않는다.
    public boolean matches(GatewayPaymentTerms terms) {

        return creationRequestHash.equals(terms.fingerprint());
    }

    // 최초 저장에만 날짜와 UUID를 생성한다. 이미 발급한 식별자는 다시 생성하지 않는다.
    @PrePersist
    private void assignPaymentId() {

        if (pgPaymentId == null) {
            pgPaymentId = LocalDate.now(PAYMENT_ID_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + UUID.randomUUID().toString();
        }
    }
}
