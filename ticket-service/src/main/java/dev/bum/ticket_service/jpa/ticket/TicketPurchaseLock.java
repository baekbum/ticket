package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자별 공연 또는 공연 그룹의 구매 매수 검증을 직렬화하기 위한 잠금 대상이다.
 * 결제 대기와 결제 완료 티켓의 매수를 저장하며, 티켓 변경과 같은 트랜잭션에서 갱신한다.
 */
@Entity
@Table(
        name = "ticket_purchase_locks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ticket_purchase_locks_user_scope",
                        columnNames = {"user_id", "limit_scope", "scope_key"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketPurchaseLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 100, nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_scope", length = 20, nullable = false, updatable = false)
    private TicketLimitScope limitScope;

    @Column(name = "scope_key", length = 100, nullable = false, updatable = false)
    private String scopeKey;

    @Column(name = "ticket_count", nullable = false)
    private long ticketCount;

    public void increase(long amount, int limit) {
        validateIncrease(amount, limit);
        ticketCount += amount;
    }

    public void validateIncrease(long amount, int limit) {
        if (amount <= 0) throw new IllegalArgumentException("추가 매수는 양수여야 합니다.");
        if (ticketCount + amount > limit) {
            throw new dev.bum.ticket_service.exception.ticket.TicketLimitExceededException(
                    "더 이상 좌석을 예매할 수 없습니다. 1인당 최대 " + limit + "매까지 예매 가능합니다.");
        }
    }

    public void decrease(long amount) {
        if (amount < 0 || amount > ticketCount) {
            throw new IllegalStateException("구매 매수 차감 정보가 일치하지 않습니다.");
        }
        ticketCount -= amount;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
