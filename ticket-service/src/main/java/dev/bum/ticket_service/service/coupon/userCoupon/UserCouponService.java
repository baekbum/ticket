package dev.bum.ticket_service.service.coupon.userCoupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public UserCouponResponse issue(IssueCouponRequest request) {
        Coupon coupon = couponRepository.selectById(request.getCouponId());

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalArgumentException("활성화된 쿠폰만 발급할 수 있습니다.");
        }

        userCouponRepository.validateNotIssued(request.getUserId(), coupon);

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = resolveUserCouponExpiresAt(request, coupon, issuedAt);

        return userCouponRepository.insert(new UserCoupon(request.getUserId(), coupon, issuedAt, expiresAt)).toResponse();
    }

    public UserCouponResponse issue(String userId, Long couponId) {
        IssueCouponRequest request = IssueCouponRequest.builder()
                .userId(userId)
                .couponId(couponId)
                .build();

        return issue(request);
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> selectByUserId(String userId) {
        return userCouponRepository.selectByUserId(userId).stream()
                .map(UserCoupon::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> selectDownloadableCoupons(String userId) {
        Set<Long> issuedCouponIds = userCouponRepository.selectByUserId(userId).stream()
                .map(UserCoupon::getCoupon)
                .map(Coupon::getCouponId)
                .collect(Collectors.toSet());

        return couponRepository.selectDownloadableCoupons(LocalDateTime.now()).stream()
                .filter(coupon -> !issuedCouponIds.contains(coupon.getCouponId()))
                .map(Coupon::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponAvailabilityResponse checkAvailable(CouponAvailabilityRequest request) {
        UserCoupon userCoupon = userCouponRepository.selectById(request.getUserCouponId());
        Coupon coupon = userCoupon.getCoupon();
        LocalDateTime now = LocalDateTime.now();

        String unavailableReason = getUnavailableReason(request, userCoupon, coupon, now);
        if (unavailableReason != null) {
            return CouponAvailabilityResponse.builder()
                    .available(false)
                    .discountAmount(0)
                    .reason(unavailableReason)
                    .build();
        }

        return CouponAvailabilityResponse.builder()
                .available(true)
                .discountAmount(calculateDiscountAmount(coupon, request.getOrderAmount()))
                .reason(null)
                .build();
    }

    @Transactional(readOnly = true)
    public CouponAvailabilityResponse checkAvailable(String currentUserId, CouponAvailabilityRequest request) {
        request.setUserId(currentUserId);
        return checkAvailable(request);
    }

    private String getUnavailableReason(CouponAvailabilityRequest request, UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        if (!request.getUserId().equals(userCoupon.getUserId())) {
            return "해당 사용자의 쿠폰이 아닙니다.";
        }

        if (userCoupon.getStatus() != UserCouponStatus.ISSUED) {
            return "이미 사용됐거나 사용할 수 없는 쿠폰입니다.";
        }

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            return "활성화된 쿠폰이 아닙니다.";
        }

        if (coupon.getValidFrom() != null && coupon.getValidFrom().isAfter(now)) {
            return "아직 사용할 수 없는 쿠폰입니다.";
        }

        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(now)) {
            return "만료된 쿠폰입니다.";
        }

        if (userCoupon.getExpiresAt() != null && userCoupon.getExpiresAt().isBefore(now)) {
            return "만료된 쿠폰입니다.";
        }

        if (coupon.getMinOrderAmount() != null && request.getOrderAmount() < coupon.getMinOrderAmount()) {
            return "쿠폰 최소 주문 금액을 충족하지 못했습니다.";
        }

        if (calculateDiscountAmount(coupon, request.getOrderAmount()) <= 0) {
            return "쿠폰 할인 금액이 유효하지 않습니다.";
        }

        return null;
    }

    private int calculateDiscountAmount(Coupon coupon, int orderAmount) {
        int discountAmount;

        if (coupon.getDiscountType() == CouponDiscountType.FIXED_AMOUNT) {
            discountAmount = coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            discountAmount = orderAmount * coupon.getDiscountValue() / 100;
            if (coupon.getMaxDiscountAmount() != null) {
                discountAmount = Math.min(discountAmount, coupon.getMaxDiscountAmount());
            }
        } else {
            throw new IllegalArgumentException("지원하지 않는 쿠폰 할인 방식입니다.");
        }

        return Math.min(discountAmount, orderAmount);
    }

    private LocalDateTime resolveUserCouponExpiresAt(IssueCouponRequest request, Coupon coupon, LocalDateTime issuedAt) {
        if (request.getExpiresAt() != null) {
            return request.getExpiresAt();
        }

        if (coupon.getValidDaysAfterIssue() != null && coupon.getValidDaysAfterIssue() > 0) {
            return issuedAt.plusDays(coupon.getValidDaysAfterIssue());
        }

        return coupon.getValidUntil();
    }
}
