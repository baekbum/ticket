package dev.bum.payment_gateway_service.jpa.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "dummy_cards",
        indexes = {
                @Index(name = "idx_dummy_cards_customer_name", columnList = "customer_name"),
                @Index(name = "idx_dummy_cards_card_company", columnList = "card_company")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dummy_cards_card_number_hash", columnNames = "card_number_hash")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dummy_card_id")
    private Long dummyCardId;

    // 프론트 결제 화면에서 사용자가 선택하는 카드사.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 30)
    private CardCompany cardCompany;

    // 카드번호 원문은 저장하지 않고, 동일 카드 식별을 위한 해시값만 저장한다.
    @Column(name = "card_number_hash", nullable = false, length = 128)
    private String cardNumberHash;

    // 화면 표시나 결제 확인용 카드번호 마지막 4자리.
    @Column(name = "card_number_last4", nullable = false, length = 4)
    private String cardNumberLast4;

    // 카드 소유자명. 더미 결제에서는 입력 고객명 검증에 사용한다.
    @Column(name = "customer_name", nullable = false, length = 50)
    private String customerName;

    // 카드 발급일자.
    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    // 카드 만료일자. 결제 승인 가능 여부 검증에 사용한다.
    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    // 이번 달 누적 사용 금액. 승인 시 결제 금액만큼 증가한다.
    @Builder.Default
    @Column(name = "current_month_used_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentMonthUsedAmount = BigDecimal.ZERO;

    // 카드 월 한도 금액.
    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    // 카드 사용 가능 여부. 분실/정지 같은 테스트 상황을 표현한다.
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // 더미 카드 row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 더미 카드 정보가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isExpired(LocalDate baseDate) {
        LocalDate targetDate = baseDate != null ? baseDate : LocalDate.now();
        return this.expiresAt.isBefore(targetDate);
    }

    public boolean canPay(BigDecimal paymentAmount) {
        if (!Boolean.TRUE.equals(this.active) || paymentAmount == null || paymentAmount.signum() <= 0) {
            return false;
        }

        return this.currentMonthUsedAmount.add(paymentAmount).compareTo(this.limitAmount) <= 0;
    }

    public void approve(BigDecimal paymentAmount) {
        if (!canPay(paymentAmount)) {
            throw new IllegalArgumentException("카드 결제 한도를 초과했거나 사용할 수 없는 카드입니다.");
        }

        this.currentMonthUsedAmount = this.currentMonthUsedAmount.add(paymentAmount);
    }

    public void deactivate() {
        this.active = false;
    }
}
