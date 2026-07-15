package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({CouponRepositoryImpl.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CouponRepositoryImplTest {

    @Autowired
    private CouponRepositoryImpl couponRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Coupon coupon;

    @BeforeEach
    void setUp() {
        coupon = couponJpaRepository.save(coupon("Summer Coupon", "SUMMER10", 10000, CouponStatus.ACTIVE));
        couponJpaRepository.save(coupon("Winter Coupon", "WINTER20", 20000, CouponStatus.INACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("쿠폰 등록")
    void coupon_insert() {
        Coupon saved = couponRepository.insert(coupon("New Coupon", "NEW10", 5000, CouponStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();

        Coupon response = couponRepository.selectById(saved.getCouponId());

        assertThat(response.getName()).isEqualTo("New Coupon");
        assertThat(response.getCode()).isEqualTo("NEW10");
        assertThat(response.getDiscountValue()).isEqualTo(5000);
    }

    @Test
    @DisplayName("쿠폰 수정")
    void coupon_update() {
        Coupon foundCoupon = couponRepository.selectById(coupon.getCouponId());
        foundCoupon.update(UpdateCouponRequest.builder()
                .name("Updated Coupon")
                .discountValue(15000)
                .build());

        Coupon updated = couponRepository.update(foundCoupon);
        entityManager.flush();
        entityManager.clear();

        Coupon response = couponRepository.selectById(updated.getCouponId());
        assertThat(response.getName()).isEqualTo("Updated Coupon");
        assertThat(response.getDiscountValue()).isEqualTo(15000);
    }

    @Test
    @DisplayName("ID로 쿠폰 조회")
    void coupon_select_by_id() {
        Coupon response = couponRepository.selectById(coupon.getCouponId());

        assertThat(response.getCouponId()).isEqualTo(coupon.getCouponId());
        assertThat(response.getCode()).isEqualTo("SUMMER10");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 쿠폰 조회 시 예외 발생")
    void coupon_select_by_id_fail() {
        assertThatThrownBy(() -> couponRepository.selectById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조건으로 쿠폰 조회")
    void coupon_select_by_cond() {
        CouponCondRequest cond = CouponCondRequest.builder()
                .code("SUMMER")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .status(CouponStatus.ACTIVE)
                .build();

        Page<Coupon> response = couponRepository.selectByCond(
                cond,
                PageRequest.of(0, 10, Sort.by("couponId").descending())
        );

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getCode()).isEqualTo("SUMMER10");
    }

    @Test
    @DisplayName("쿠폰 코드 존재 여부 확인")
    void coupon_exists_by_code() {
        boolean response = couponRepository.existsByCode("SUMMER10");

        assertThat(response).isTrue();
    }

    @Test
    @DisplayName("특정 ID를 제외한 쿠폰 코드 존재 여부 확인")
    void coupon_exists_by_code_except_id() {
        boolean sameCouponResponse = couponRepository.existsByCodeExceptId("SUMMER10", coupon.getCouponId());
        boolean otherCouponResponse = couponRepository.existsByCodeExceptId("WINTER20", coupon.getCouponId());

        assertThat(sameCouponResponse).isFalse();
        assertThat(otherCouponResponse).isTrue();
    }

    private Coupon coupon(String name, String code, Integer discountValue, CouponStatus status) {
        return Coupon.builder()
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
