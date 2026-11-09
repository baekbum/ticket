package dev.bum.payment_gateway_service.jpa.gateway;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GatewayPaymentJpaRepository extends JpaRepository<GatewayPayment, String> {

    Optional<GatewayPayment> findByPgPaymentIdAndMerchantMerchantId(String pgPaymentId, Long merchantId);

    Optional<GatewayPayment> findByMerchantMerchantIdAndMerchantPaymentNo(Long merchantId, String paymentNo);

    // 금융 원장의 입금과 PG 확정 이력 간 대사는 은행이 아닌 PG 책임으로 조회한다.
    @Query(value = """
            select a.pg_payment_id from dummy_virtual_accounts a
             join gateway_payments p on p.pg_payment_id = a.pg_payment_id
             where a.deposited_at is not null
               and not exists (select 1 from gateway_payment_histories h
                                where h.pg_payment_id = p.pg_payment_id and h.operation_type = 'DEPOSIT')
             order by a.deposited_at, a.pg_payment_id
            """, nativeQuery = true)
    List<String> findUnconfirmedDepositIds(Pageable pageable);

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
