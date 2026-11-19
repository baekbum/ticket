package dev.bum.payment_gateway_service.jpa.virtualAccount;


import dev.bum.common.service.ticket.payment.enums.BankCompany;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_account_issue_gates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccountIssueGate {

    // 계좌가 아직 없어도 잠글 수 있는 계좌 발급 원거래 식별자.
    @Id
    @Column(name = "pg_payment_id", length = 44, updatable = false)
    private String pgPaymentId;

    // 최초 계좌 발급 요청의 은행·금액·기한.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", nullable = false, length = 30, updatable = false)
    private BankCompany bankCompany;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    // 전환이 먼저 확정되면 같은 PG 거래의 늦은 계좌 발급을 영구 차단한다.
    @Column(nullable = false)
    private boolean closed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static BankAccountIssueGate open(BankVirtualAccountIssueRequest request) {

        BankAccountIssueGate gate = new BankAccountIssueGate();
        gate.pgPaymentId = request.pgPaymentId();
        gate.bankCompany = request.bankCompany();
        gate.amount = request.amount();
        gate.expiresAt = request.expiresAt();

        return gate;
    }

    public void validate(BankVirtualAccountIssueRequest request) {

        if (!pgPaymentId.equals(request.pgPaymentId()) || !bankCompany.equals(request.bankCompany())
                || amount.compareTo(request.amount()) != 0 || !expiresAt.equals(request.expiresAt())) {
            throw new IllegalArgumentException("계좌 발급 차단의 최초 원거래 조건과 다릅니다.");
        }
    }

    public void close() {

        closed = true;
    }
}
