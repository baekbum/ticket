package dev.bum.ticket_service.jpa.coupon.coupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository {

    Coupon insert(Coupon coupon);

    Coupon update(Coupon coupon);

    Coupon selectById(Long couponId);

    Page<Coupon> selectByCond(CouponCondRequest cond, Pageable pageable);

    List<Coupon> selectDownloadableCoupons(LocalDateTime now);

    long expireExpiredCoupons(LocalDateTime now);

    boolean existsByCode(String code);

    boolean existsByCodeExceptId(String code, Long couponId);
}
