package dev.bum.payment_gateway_service.jpa.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantNotificationEndpointJpaRepository extends JpaRepository<MerchantNotificationEndpoint, Long> {

    // 같은 대상 키도 소유 가맹점을 함께 확인한다.
    Optional<MerchantNotificationEndpoint> findByMerchantMerchantIdAndTargetKey(Long merchantId, String targetKey);
}
