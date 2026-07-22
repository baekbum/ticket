package dev.bum.ticket_service.service.coupon.userCoupon;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateUserCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    /**
     * 관리자 요청 정보로 사용자에게 쿠폰을 발급한다.
     */
    @AuditLog(action = "USER_COUPON_ISSUE", targetType = "USER_COUPON")
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

    /**
     * 로그인 사용자 기준으로 지정 쿠폰을 발급한다.
     */
    @AuditLog(action = "USER_COUPON_ISSUE", targetType = "USER_COUPON")
    public UserCouponResponse issue(String userId, Long couponId) {
        IssueCouponRequest request = IssueCouponRequest.builder()
                .userId(userId)
                .couponId(couponId)
                .build();

        return issue(request);
    }

    /**
     * 사용자 쿠폰의 상태와 만료일을 수정한다.
     */
    @AuditLog(action = "USER_COUPON_UPDATE", targetType = "USER_COUPON")
    public UserCouponResponse update(Long userCouponId, UpdateUserCouponRequest request) {
        UserCoupon userCoupon = userCouponRepository.selectById(userCouponId);
        AuditDataMapper.setChangedData(userCoupon, request);
        userCoupon.update(request);
        return userCouponRepository.update(userCoupon).toResponse();
    }

    /**
     * 사용자 ID로 보유 쿠폰 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<UserCouponResponse> selectByUserId(String userId) {
        return userCouponRepository.selectByUserId(userId).stream()
                .map(UserCoupon::toResponse)
                .toList();
    }

    /**
     * 검색 조건과 페이징 조건으로 사용자 쿠폰 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<UserCouponResponse> selectByCond(UserCouponCondRequest cond) {
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<UserCouponResponse> page = userCouponRepository.selectByCond(cond, pageRequest).map(UserCoupon::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * 사용자가 아직 발급받지 않았고 현재 발급 가능한 쿠폰 목록을 조회한다.
     */
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

    /**
     * 사용자 쿠폰이 주문 금액 기준으로 사용 가능한지 확인하고 할인 금액을 계산한다.
     */
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

    /**
     * 로그인 사용자 ID를 요청에 주입한 뒤 쿠폰 사용 가능 여부를 확인한다.
     */
    @Transactional(readOnly = true)
    public CouponAvailabilityResponse checkAvailable(String currentUserId, CouponAvailabilityRequest request) {
        request.setUserId(currentUserId);
        return checkAvailable(request);
    }

    /**
     * 쿠폰 소유자, 상태, 유효 기간, 최소 주문 금액을 검사해 사용 불가 사유를 반환한다.
     */
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

    /**
     * 쿠폰 할인 정책에 따라 주문 금액에서 차감할 할인 금액을 계산한다.
     */
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

    /**
     * 요청 만료일, 발급 후 유효 일수, 쿠폰 정책 만료일 순서로 사용자 쿠폰 만료 시각을 결정한다.
     */
    private LocalDateTime resolveUserCouponExpiresAt(IssueCouponRequest request, Coupon coupon, LocalDateTime issuedAt) {
        if (request.getExpiresAt() != null) {
            return request.getExpiresAt();
        }

        if (coupon.getValidDaysAfterIssue() != null && coupon.getValidDaysAfterIssue() > 0) {
            return issuedAt.plusDays(coupon.getValidDaysAfterIssue());
        }

        return coupon.getValidUntil();
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환하고 기본 정렬을 적용한다.
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.by(Sort.Order.desc("userCouponId"));
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");
                if (infos.length == 2) {
                    orders.add(new Sort.Order(Sort.Direction.fromString(infos[1]), infos[0]));
                }
            }
            if (!orders.isEmpty()) {
                sort = Sort.by(orders);
            }
        }

        return sort;
    }
}
