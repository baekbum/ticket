package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepository;
import dev.bum.ticket_service.service.coupon.coupon.CouponService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Test
    @DisplayName("쿠폰 등록")
    void coupon_insert() {
        InsertCouponRequest request = insertRequest();
        Coupon savedCoupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);

        given(couponRepository.existsByCode("SUMMER10")).willReturn(false);
        given(couponRepository.insert(any())).willReturn(savedCoupon);

        CouponResponse response = couponService.insert(request);

        assertThat(response.getCouponId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("SUMMER10");
        then(couponRepository).should().existsByCode("SUMMER10");
        then(couponRepository).should().insert(any(Coupon.class));
    }

    @Test
    @DisplayName("이미 존재하는 코드로 쿠폰 등록 시 예외 발생")
    void coupon_insert_fail_duplicate_code() {
        InsertCouponRequest request = insertRequest();

        given(couponRepository.existsByCode("SUMMER10")).willReturn(true);

        assertThatThrownBy(() -> couponService.insert(request))
                .isInstanceOf(IllegalArgumentException.class);
        then(couponRepository).should().existsByCode("SUMMER10");
        then(couponRepository).should(never()).insert(any());
    }

    @Test
    @DisplayName("쿠폰 수정")
    void coupon_update() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);
        UpdateCouponRequest request = UpdateCouponRequest.builder()
                .name("Updated Coupon")
                .code("SUMMER15")
                .discountValue(15000)
                .build();

        given(couponRepository.selectById(1L)).willReturn(coupon);
        given(couponRepository.existsByCodeExceptId("SUMMER15", 1L)).willReturn(false);
        given(couponRepository.update(coupon)).willReturn(coupon);

        CouponResponse response = couponService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Updated Coupon");
        assertThat(response.getCode()).isEqualTo("SUMMER15");
        assertThat(response.getDiscountValue()).isEqualTo(15000);
        then(couponRepository).should().selectById(1L);
        then(couponRepository).should().existsByCodeExceptId("SUMMER15", 1L);
        then(couponRepository).should().update(coupon);
    }

    @Test
    @DisplayName("ID로 쿠폰 조회")
    void coupon_select_by_id() {
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);

        given(couponRepository.selectById(1L)).willReturn(coupon);

        CouponResponse response = couponService.selectById(1L);

        assertThat(response.getCouponId()).isEqualTo(1L);
        then(couponRepository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 쿠폰 조회")
    void coupon_select_by_cond() {
        CouponCondRequest cond = CouponCondRequest.builder()
                .code("SUMMER")
                .page(0)
                .size(10)
                .sort(List.of("couponId-desc"))
                .build();
        Coupon coupon = coupon(1L, "Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE);

        given(couponRepository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(coupon)));

        CustomPageResponse<CouponResponse> response = couponService.selectByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(couponRepository).should().selectByCond(
                eq(cond),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("couponId") != null)
        );
    }

    private InsertCouponRequest insertRequest() {
        return InsertCouponRequest.builder()
                .name("Summer Coupon")
                .code("SUMMER10")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .minOrderAmount(10000)
                .validFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
                .validUntil(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(CouponStatus.ACTIVE)
                .build();
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
}
