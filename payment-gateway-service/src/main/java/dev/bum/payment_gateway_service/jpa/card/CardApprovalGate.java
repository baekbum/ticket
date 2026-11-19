package dev.bum.payment_gateway_service.jpa.card;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalGateRequest;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_approval_gates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardApprovalGate {

    // 승인 이력이 아직 없어도 잠글 수 있는 카드 원거래 식별자.
    @Id
    @Column(name = "pg_payment_id", length = 44, updatable = false)
    private String pgPaymentId;

    // 최초 승인 요청 조건. 카드 원문은 저장하지 않는다.
    @Column(name = "buyer_reference", nullable = false, length = 50, updatable = false)
    private String buyerReference;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    // 전환이 먼저 확정되면 같은 PG 거래의 늦은 승인을 영구 차단한다.
    @Column(nullable = false)
    private boolean closed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CardApprovalGate open(FinancialCardApprovalGateRequest request) {

        request.validate();
        CardApprovalGate gate = new CardApprovalGate();
        gate.pgPaymentId = request.pgPaymentId();
        gate.buyerReference = request.buyerReference();
        gate.amount = request.amount();
        gate.expiresAt = request.expiresAt();

        return gate;
    }

    public void validate(FinancialCardApprovalGateRequest request) {

        if (!pgPaymentId.equals(request.pgPaymentId()) || !buyerReference.equals(request.buyerReference())
                || amount.compareTo(request.amount()) != 0 || !expiresAt.equals(request.expiresAt())) {
            throw new GatewayPaymentConflictException("카드 승인 차단의 최초 원거래 조건과 다릅니다.");
        }
    }

    public void close() {

        closed = true;
    }
}
