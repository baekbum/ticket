package dev.bum.ticket_service.jpa.coupon.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndCouponIdNot(String code, Long couponId);

}
