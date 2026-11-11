package dev.bum.payment_gateway_service.jpa.gateway;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GatewayPaymentJpaRepository extends JpaRepository<GatewayPayment, String> {

    Optional<GatewayPayment> findByPgPaymentIdAndMerchantMerchantId(String pgPaymentId, Long merchantId);

    Optional<GatewayPayment> findByMerchantMerchantIdAndMerchantPaymentNo(Long merchantId, String paymentNo);

    // 인증된 은행 사실의 PG 거래를 잠근다. 일반 가맹점 API는 소유 범위 조회를 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GatewayPayment p where p.pgPaymentId = :id")
    Optional<GatewayPayment> findForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GatewayPayment p where p.pgPaymentId = :paymentId and p.merchant.merchantId = :merchantId")
    Optional<GatewayPayment> findOwnedForUpdate(
            @Param("merchantId") Long merchantId,
            @Param("paymentId") String paymentId
    );
}
