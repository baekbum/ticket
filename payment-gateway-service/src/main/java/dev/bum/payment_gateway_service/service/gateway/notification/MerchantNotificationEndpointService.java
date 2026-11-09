package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.payment_gateway_service.jpa.merchant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MerchantNotificationEndpointService {

    // 통보 대상을 소유할 등록 가맹점.
    private final MerchantJpaRepository merchants;
    // YAML 대신 DB에서 관리하는 통보 대상.
    private final MerchantNotificationEndpointJpaRepository endpoints;
    // 발신 토큰은 암호문으로만 저장한다.
    private final MerchantNotificationTokenCipher cipher;

    /**
     * 관리용 내부 서비스에서 가맹점별 통보 대상을 등록한다. 결제 API에는 노출하지 않는다.
     */
    public Long register(Long merchantId, String targetKey, String url, String serviceToken) {

        if (targetKey == null || targetKey.isBlank() || targetKey.length() > 100
                || !targetKey.equals(targetKey.strip())) {
            throw new IllegalArgumentException("가맹점 통보 대상 키가 올바르지 않습니다.");
        }

        MerchantNotificationTargetResolver.validateUrl(url);
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 가맹점이 필요합니다."));
        MerchantNotificationEndpoint endpoint = MerchantNotificationEndpoint.register(
                merchant, targetKey, url, cipher.encrypt(serviceToken));

        return endpoints.saveAndFlush(endpoint).getEndpointId();
    }

    /**
     * 기존 대상 키와 소유 가맹점을 유지한 채 URL과 인증값을 교체한다.
     */
    public void update(Long merchantId, String targetKey, String url, String serviceToken) {

        MerchantNotificationTargetResolver.validateUrl(url);
        MerchantNotificationEndpoint endpoint = find(merchantId, targetKey);
        endpoint.changeDelivery(url, cipher.encrypt(serviceToken));
    }

    /**
     * 중단한 통보는 Outbox에 유지하고 활성화 후 같은 이벤트로 재시도한다.
     */
    public void changeActive(Long merchantId, String targetKey, boolean active) {

        find(merchantId, targetKey).changeActive(active);
    }

    /**
     * 가맹점 소유 범위 안에서만 대상 설정을 찾는다.
     */
    private MerchantNotificationEndpoint find(Long merchantId, String targetKey) {

        return endpoints.findByMerchantMerchantIdAndTargetKey(merchantId, targetKey)
                .orElseThrow(() -> new IllegalArgumentException("가맹점 통보 대상 설정이 필요합니다."));
    }
}
