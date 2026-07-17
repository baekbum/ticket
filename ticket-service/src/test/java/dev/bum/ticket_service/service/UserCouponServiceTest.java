package dev.bum.ticket_service.service;

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
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.service.coupon.userCoupon.UserCouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserCouponServiceTest {

    @InjectMocks
    private UserCouponService userCouponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("사용자에게 쿠폰 발급")
    void user_coupon_issue() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        IssueCouponRequest request = IssueCouponRequest.builder()
                .userId("user01")
                .couponId(1L)
                .build();
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);

        given(couponRepository.selectById(1L)).willReturn(coupon);
        given(userCouponRepository.insert(any())).willReturn(userCoupon);

        UserCouponResponse response = userCouponService.issue(request);

        assertThat(response.getUserCouponId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user01");
        then(couponRepository).should().selectById(1L);
        then(userCouponRepository).should().validateNotIssued("user01", coupon);
        then(userCouponRepository).should().insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("로그인 사용자 기준으로 쿠폰 다운로드")
    void user_coupon_download() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);

        given(couponRepository.selectById(1L)).willReturn(coupon);
        given(userCouponRepository.insert(any())).willReturn(userCoupon);

        UserCouponResponse response = userCouponService.issue("user01", 1L);

        assertThat(response.getUserCouponId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user01");
        then(couponRepository).should().selectById(1L);
        then(userCouponRepository).should().validateNotIssued("user01", coupon);
        then(userCouponRepository).should().insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("사용자 ID로 보유 쿠폰 조회")
    void user_coupon_select_by_user_id() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);

        given(userCouponRepository.selectByUserId("user01")).willReturn(List.of(userCoupon));

        List<UserCouponResponse> response = userCouponService.selectByUserId("user01");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getUserId()).isEqualTo("user01");
        then(userCouponRepository).should().selectByUserId("user01");
    }

    @Test
    @DisplayName("유저 쿠폰 조건 조회")
    void user_coupon_select_by_cond() {
        UserCouponCondRequest cond = UserCouponCondRequest.builder()
                .userId("user01")
                .couponName("Summer")
                .status(UserCouponStatus.ISSUED)
                .page(0)
                .size(10)
                .sort(List.of("userCouponId-desc"))
                .build();
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);

        given(userCouponRepository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(userCoupon)));

        CustomPageResponse<UserCouponResponse> response = userCouponService.selectByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getUserCouponId()).isEqualTo(1L);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(userCouponRepository).should().selectByCond(
                eq(cond),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("userCouponId") != null)
        );
    }

    @Test
    @DisplayName("유저 쿠폰 상태와 만료일 수정")
    void user_coupon_update() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);
        UpdateUserCouponRequest request = UpdateUserCouponRequest.builder()
                .status(UserCouponStatus.EXPIRED)
                .expiresAt(LocalDateTime.of(2026, 2, 1, 0, 0))
                .build();

        given(userCouponRepository.selectById(1L)).willReturn(userCoupon);
        given(userCouponRepository.update(userCoupon)).willReturn(userCoupon);

        UserCouponResponse response = userCouponService.update(1L, request);

        assertThat(response.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
        assertThat(response.getExpiresAt()).isEqualTo("2026-02-01 00:00:00");
        then(userCouponRepository).should().selectById(1L);
        then(userCouponRepository).should().update(userCoupon);
    }

    @Test
    @DisplayName("다운로드 가능한 쿠폰 조회 시 이미 받은 쿠폰 제외")
    void user_coupon_select_downloadable() {
        Coupon issuedCoupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        Coupon downloadableCoupon = coupon(2L, "Winter Coupon", "WINTER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", issuedCoupon, UserCouponStatus.ISSUED);

        given(userCouponRepository.selectByUserId("user01")).willReturn(List.of(userCoupon));
        given(couponRepository.selectDownloadableCoupons(any())).willReturn(List.of(issuedCoupon, downloadableCoupon));

        List<CouponResponse> response = userCouponService.selectDownloadableCoupons("user01");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCouponId()).isEqualTo(2L);
        then(userCouponRepository).should().selectByUserId("user01");
        then(couponRepository).should().selectDownloadableCoupons(any());
    }

    @Test
    @DisplayName("정액 쿠폰 사용 가능 여부 확인")
    void user_coupon_check_available_fixed_amount() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);
        CouponAvailabilityRequest request = CouponAvailabilityRequest.builder()
                .userId("user01")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();

        given(userCouponRepository.selectById(1L)).willReturn(userCoupon);

        CouponAvailabilityResponse response = userCouponService.checkAvailable(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualTo(10000);
        then(userCouponRepository).should().selectById(1L);
    }

    @Test
    @DisplayName("로그인 사용자 기준으로 쿠폰 사용 가능 여부 확인")
    void user_coupon_check_available_with_current_user() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "user01", coupon, UserCouponStatus.ISSUED);
        CouponAvailabilityRequest request = CouponAvailabilityRequest.builder()
                .userId("other-user")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();

        given(userCouponRepository.selectById(1L)).willReturn(userCoupon);

        CouponAvailabilityResponse response = userCouponService.checkAvailable("user01", request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(request.getUserId()).isEqualTo("user01");
        then(userCouponRepository).should().selectById(1L);
    }

    @Test
    @DisplayName("사용자 쿠폰 소유자가 다르면 사용 불가")
    void user_coupon_check_available_fail_with_wrong_user() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UserCoupon userCoupon = userCoupon(1L, "other-user", coupon, UserCouponStatus.ISSUED);
        CouponAvailabilityRequest request = CouponAvailabilityRequest.builder()
                .userId("user01")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();

        given(userCouponRepository.selectById(1L)).willReturn(userCoupon);

        CouponAvailabilityResponse response = userCouponService.checkAvailable(request);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getDiscountAmount()).isZero();
        assertThat(response.getReason()).isNotNull();
        then(userCouponRepository).should().selectById(1L);
    }

    private Coupon coupon(Long couponId, String name, String code, Integer discountValue, CouponStatus status) {
        return Coupon.builder()
                .couponId(couponId)
                .name(name)
                .code(code)
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(discountValue)
                .minOrderAmount(10000)
                .validFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
                .validUntil(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(status)
                .build();
    }

    private UserCoupon userCoupon(Long userCouponId, String userId, Coupon coupon, UserCouponStatus status) {
        return UserCoupon.builder()
                .userCouponId(userCouponId)
                .userId(userId)
                .coupon(coupon)
                .status(status)
                .issuedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .expiresAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                .build();
    }
}
