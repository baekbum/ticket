package dev.bum.ticket_service.jpa.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(String userId);

    Optional<UserCoupon> findByUserIdAndCoupon(String userId, Coupon coupon);
}
