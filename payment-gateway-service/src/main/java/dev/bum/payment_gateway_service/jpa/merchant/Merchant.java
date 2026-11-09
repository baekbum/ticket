package dev.bum.payment_gateway_service.jpa.merchant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "merchants", uniqueConstraints =
        @UniqueConstraint(name = "uk_merchants_credential_key", columnNames = "credential_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant {

    // 숫자 PK 자체를 공개 가맹점 코드로 사용한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_id")
    private Long merchantId;

    // 표시 이름은 서로 다른 가맹점 사이에서 중복될 수 있다.
    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    // 신규 거래 처리를 허용하는 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MerchantStatus status;

    // 비밀값을 포함하지 않는 서버 자격증명 그룹의 설정 키.
    @Column(name = "credential_key", nullable = false, length = 64, updatable = false)
    private String credentialKey;

    // 가맹점이 사용하는 통보 DTO의 필드 구성·응답 규칙을 식별하는 계약 버전 코드.
    // 현재는 CARD_NOTIFICATION_V1 지원 여부만 검사한다. 클래스명이나 자동 DTO 선택 값이 아니다.
    @Column(name = "notification_contract", nullable = false, length = 40)
    private String notificationContract;

    // merchant_notification_endpoints에서 이 가맹점의 등록 URL·암호화 인증값을 찾는 대상 키.
    @Column(name = "notification_target_key", nullable = false, length = 100)
    private String notificationTargetKey;

    // 최초 등록 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 비밀값 없이 신규 가맹점을 등록하고 초기에는 사용을 중단한다.
    public static Merchant register(String name, String credentialKey, String notificationContract, String targetKey) {

        Merchant merchant = new Merchant();
        merchant.merchantName = required(name, 100);
        merchant.credentialKey = required(credentialKey, 64);
        merchant.notificationContract = required(notificationContract, 40);
        merchant.notificationTargetKey = required(targetKey, 100);
        merchant.status = MerchantStatus.SUSPENDED;

        return merchant;
    }

    // 등록 설정 확인 후 신규 결제 처리를 허용한다.
    public void activate() {

        status = MerchantStatus.ACTIVE;
    }

    // 기존 거래의 조회·정리 가능성은 유지하며 신규 처리를 중단한다.
    public void suspend() {

        status = MerchantStatus.SUSPENDED;
    }

    // 공백·길이가 잘못된 등록 설정을 거절한다.
    private static String required(String value, int maxLength) {

        if (value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.strip())) {
            throw new IllegalArgumentException("가맹점 등록 정보가 올바르지 않습니다.");
        }

        return value;
    }
}
