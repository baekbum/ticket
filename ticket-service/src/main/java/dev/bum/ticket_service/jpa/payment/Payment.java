package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_reservation_id", columnNames = "reservation_id"),
                @UniqueConstraint(name = "uk_payments_payment_no", columnNames = "payment_no")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    // 할인/배송비까지 계산된 최종 결제 요청 금액.
    @Column(nullable = false)
    private Integer amount;

    // 같은 결제 요청이 중복 처리되지 않도록 클라이언트나 서버가 발급하는 멱등성 키.
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    // 무통장 입금 시 안내할 은행명.
    @Column(name = "bank_name", length = 50)
    private String bankName;

    // 무통장 입금 시 안내할 가상/입금 계좌 번호.
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    // 입금 확인에 사용할 입금자명.
    @Column(name = "depositor_name", length = 50)
    private String depositorName;

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

    public PaymentResponse toResponse() {
        return PaymentResponse.builder()
                .paymentId(this.paymentId)
                .reservationId(this.reservation != null ? this.reservation.getReservationId() : null)
                .orderId(this.reservation != null ? this.reservation.getOrderId() : null)
                .paymentNo(this.paymentNo)
                .method(this.method)
                .status(this.status)
                .amount(this.amount)
                .bankName(this.bankName)
                .accountNumber(this.accountNumber)
                .depositorName(this.depositorName)
                .requestedAt(formatDateTime(this.requestedAt))
                .paidAt(formatDateTime(this.paidAt))
                .expiresAt(formatDateTime(this.expiresAt))
                .build();
    }

    public void waitDeposit(String bankName, String accountNumber, String depositorName, LocalDateTime expiresAt) {
        this.status = PaymentStatus.WAITING_DEPOSIT;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.depositorName = depositorName;
        this.expiresAt = expiresAt;
    }

    public void complete(LocalDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    public void expire() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}
