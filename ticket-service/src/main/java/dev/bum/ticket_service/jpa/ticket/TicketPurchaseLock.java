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
 * 매수는 저장하지 않고 기존 티켓에서 조회하며, 잠금 행은 트랜잭션 종료 후에도 유지한다.
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
