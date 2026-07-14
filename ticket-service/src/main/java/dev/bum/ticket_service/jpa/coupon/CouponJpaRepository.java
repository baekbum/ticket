package dev.bum.ticket_service.jpa.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndCouponIdNot(String code, Long couponId);

    Optional<Coupon> findByCode(String code);
}
