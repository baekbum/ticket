package dev.bum.ticket_service.jpa.coupon.userCoupon;

import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCoupon uc where uc.userCouponId = :userCouponId")
    Optional<UserCoupon> findByIdForUpdate(@Param("userCouponId") Long userCouponId);

    List<UserCoupon> findByUserId(String userId);

    Optional<UserCoupon> findByUserIdAndCoupon(String userId, Coupon coupon);
}
