package dev.bum.ticket_service.jpa.coupon.userCoupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponCondRequest;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserCouponRepository {

    UserCoupon insert(UserCoupon userCoupon);

    UserCoupon update(UserCoupon userCoupon);

    UserCoupon selectById(Long userCouponId);

    List<UserCoupon> selectByUserId(String userId);

    Page<UserCoupon> selectByCond(UserCouponCondRequest cond, Pageable pageable);

    void validateNotIssued(String userId, Coupon coupon);
}
