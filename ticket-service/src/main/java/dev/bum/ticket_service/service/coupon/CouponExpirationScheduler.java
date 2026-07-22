package dev.bum.ticket_service.service.coupon;

import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpirationScheduler {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 만료 시각이 지난 쿠폰 정책과 사용자 쿠폰을 주기적으로 만료 처리한다.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireCouponData() {
        LocalDateTime now = LocalDateTime.now();

        long expiredCouponCount = couponRepository.expireExpiredCoupons(now);
        long expiredUserCouponCount = userCouponRepository.expireExpiredUserCoupons(now);

        if (expiredCouponCount > 0 || expiredUserCouponCount > 0) {
            log.info("[COUPON][EXPIRE] expiredCoupons={}, expiredUserCoupons={}, now={}",
                    expiredCouponCount, expiredUserCouponCount, now);
        }
    }
}
