package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantNotificationEndpoint;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantNotificationEndpointJpaRepository;
import dev.bum.payment_gateway_service.dto.gateway.MerchantNotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class MerchantNotificationTargetResolver {

    // 가맹점 ID와 대상 키로 소유 관계를 제한하는 DB 등록 설정.
    private final MerchantNotificationEndpointJpaRepository endpoints;
    // 전송 직전에만 발신 인증값을 복호화한다.
    private final MerchantNotificationTokenCipher cipher;

    /**
     * 결제 요청이 제공한 URL 대신 등록된 계약·대상 키에서 전체 수신 URL을 해석한다.
     */
    @Transactional(readOnly = true)
    public MerchantNotificationTarget resolve(Long merchantId, String contract, String targetKey) {

        if (!CardPaymentNotification.CONTRACT.equals(contract)) {
            throw new IllegalArgumentException("지원하지 않는 가맹점 통보 규격입니다.");
        }

        MerchantNotificationEndpoint target = endpoints.findByMerchantMerchantIdAndTargetKey(merchantId, targetKey)
                .filter(MerchantNotificationEndpoint::isActive)
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 가맹점 통보 대상 설정이 필요합니다."));

        return new MerchantNotificationTarget(validateUrl(target.getNotificationUrl()),
                cipher.decrypt(target.getServiceTokenCiphertext()));
    }

    /**
     * 등록·조회 시 모두 전체 HTTP API 주소를 검증하고 사용자 정보나 우회 주소를 거절한다.
     */
    public static URI validateUrl(String url) {

        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new IllegalArgumentException("가맹점 통보 대상 주소가 올바르지 않습니다.");
        }

        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("가맹점 통보 대상 주소가 올바르지 않습니다.");
        }

        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("가맹점 통보 대상 주소가 올바르지 않습니다.");
        }

        return uri;
    }
}
