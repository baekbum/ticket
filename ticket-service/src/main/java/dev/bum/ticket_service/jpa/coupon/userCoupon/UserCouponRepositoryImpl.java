package dev.bum.ticket_service.jpa.coupon.userCoupon;

import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public UserCoupon insert(UserCoupon userCoupon) {
        return jpaRepository.save(userCoupon);
    }

    @Override
    public UserCoupon selectById(Long userCouponId) {
        return jpaRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 쿠폰이 존재하지 않습니다."));
    }

    @Override
    public List<UserCoupon> selectByUserId(String userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public void validateNotIssued(String userId, Coupon coupon) {
        jpaRepository.findByUserIdAndCoupon(userId, coupon)
                .ifPresent(userCoupon -> {
                    throw new IllegalArgumentException("이미 발급된 쿠폰입니다.");
                });
    }
}
