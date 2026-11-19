package dev.bum.payment_gateway_service.jpa.merchant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_notification_endpoints", uniqueConstraints =
        @UniqueConstraint(name = "uk_merchant_notification_endpoints_target", columnNames = {"merchant_id", "target_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantNotificationEndpoint {

    // 등록한 통보 대상의 내부 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "endpoint_id")
    private Long endpointId;

    // 이 주소와 인증값을 사용할 수 있는 가맹점. 다른 가맹점의 같은 대상 키와 구분한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, updatable = false)
    private Merchant merchant;

    // Merchant와 Outbox가 참조하는 가맹점 내부 대상 키. 재시도 중에는 변경하지 않는다.
    @Column(name = "target_key", nullable = false, length = 100, updatable = false)
    private String targetKey;

    // 관리자가 등록한 전체 수신 API URL. 일반 결제 요청으로 변경하지 않는다.
    @Column(name = "notification_url", nullable = false, length = 2048)
    private String notificationUrl;

    // AES-GCM으로 암호화한 발신 인증 토큰. 암호화 키는 DB 외부의 환경 설정에서 관리한다.
    @Column(name = "service_token_ciphertext", nullable = false, columnDefinition = "text")
    private String serviceTokenCiphertext;

    // 비활성 대상은 금융 실패로 처리하지 않고 통보 재시도 대상으로 남긴다.
    @Column(nullable = false)
    private boolean active;

    // 최초 등록 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // URL·인증값·활성 상태의 마지막 변경 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 평문 인증값을 받지 않고 검증한 주소와 암호문으로 대상을 등록한다.
    public static MerchantNotificationEndpoint register(
            Merchant merchant,
            String targetKey,
            String url,
            String ciphertext
    ) {

        MerchantNotificationEndpoint endpoint = new MerchantNotificationEndpoint();
        endpoint.merchant = merchant;
        endpoint.targetKey = targetKey;
        endpoint.changeDelivery(url, ciphertext);
        endpoint.active = true;

        return endpoint;
    }

    // 대상 키는 유지하고 이후 재시도부터 사용할 주소·인증값을 교체한다.
    public void changeDelivery(String url, String ciphertext) {

        notificationUrl = url;
        serviceTokenCiphertext = ciphertext;
    }

    // 통보 대상 사용을 재개하거나 일시 중단한다.
    public void changeActive(boolean active) {

        this.active = active;
    }
}
