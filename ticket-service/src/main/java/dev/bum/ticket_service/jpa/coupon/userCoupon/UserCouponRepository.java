package dev.bum.ticket_service.jpa.coupon.userCoupon;

import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;

import java.util.List;

public interface UserCouponRepository {

    UserCoupon insert(UserCoupon userCoupon);

    UserCoupon selectById(Long userCouponId);

    List<UserCoupon> selectByUserId(String userId);

    void validateNotIssued(String userId, Coupon coupon);
}
