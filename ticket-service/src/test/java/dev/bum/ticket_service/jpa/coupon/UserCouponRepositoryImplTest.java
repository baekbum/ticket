package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateUserCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepositoryImpl;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({UserCouponRepositoryImpl.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserCouponRepositoryImplTest {

    @Autowired
    private UserCouponRepositoryImpl userCouponRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Coupon summerCoupon;
    private Coupon winterCoupon;
    private UserCoupon issuedUserCoupon;

    @BeforeEach
    void setUp() {
        summerCoupon = couponJpaRepository.save(coupon("Summer Coupon", "SUMMER10", 10000));
        winterCoupon = couponJpaRepository.save(coupon("Winter Coupon", "WINTER20", 20000));

        issuedUserCoupon = userCouponJpaRepository.save(userCoupon(
                "user01",
                summerCoupon,
                UserCouponStatus.ISSUED,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                null,
                LocalDateTime.of(2026, 12, 31, 23, 59)
        ));
        userCouponJpaRepository.save(userCoupon(
                "user02",
                winterCoupon,
                UserCouponStatus.USED,
                LocalDateTime.of(2026, 1, 2, 10, 0),
                LocalDateTime.of(2026, 1, 3, 10, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59)
        ));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("유저 쿠폰 저장")
    void user_coupon_insert() {
        UserCoupon saved = userCouponRepository.insert(userCoupon(
                "user03",
                summerCoupon,
                UserCouponStatus.ISSUED,
                LocalDateTime.of(2026, 1, 4, 10, 0),
                null,
                LocalDateTime.of(2026, 2, 1, 0, 0)
        ));
        entityManager.flush();
        entityManager.clear();

        UserCoupon response = userCouponRepository.selectById(saved.getUserCouponId());

        assertThat(response.getUserId()).isEqualTo("user03");
        assertThat(response.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(response.getCoupon().getCode()).isEqualTo("SUMMER10");
    }

    @Test
    @DisplayName("유저 쿠폰 ID로 조회")
    void user_coupon_select_by_id() {
        UserCoupon response = userCouponRepository.selectById(issuedUserCoupon.getUserCouponId());

        assertThat(response.getUserCouponId()).isEqualTo(issuedUserCoupon.getUserCouponId());
        assertThat(response.getUserId()).isEqualTo("user01");
    }

    @Test
    @DisplayName("존재하지 않는 유저 쿠폰 ID로 조회하면 예외 발생")
    void user_coupon_select_by_id_fail() {
        assertThatThrownBy(() -> userCouponRepository.selectById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유저 ID로 유저 쿠폰 조회")
    void user_coupon_select_by_user_id() {
        List<UserCoupon> response = userCouponRepository.selectByUserId("user01");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCoupon().getCode()).isEqualTo("SUMMER10");
    }

    @Test
    @DisplayName("조건으로 유저 쿠폰 조회")
    void user_coupon_select_by_cond() {
        UserCouponCondRequest cond = UserCouponCondRequest.builder()
                .userId("user01")
                .couponName("Summer")
                .couponCode("SUMMER")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .status(UserCouponStatus.ISSUED)
                .issuedAt(LocalDate.of(2026, 1, 1))
                .expiresAt(LocalDate.of(2026, 12, 31))
                .build();

        Page<UserCoupon> response = userCouponRepository.selectByCond(
                cond,
                PageRequest.of(0, 10, Sort.by("userCouponId").descending())
        );

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getUserId()).isEqualTo("user01");
        assertThat(response.getContent().get(0).getCoupon().getName()).isEqualTo("Summer Coupon");
    }

    @Test
    @DisplayName("유저 쿠폰 상태와 만료일 수정")
    void user_coupon_update() {
        UserCoupon found = userCouponRepository.selectById(issuedUserCoupon.getUserCouponId());
        found.update(UpdateUserCouponRequest.builder()
                .status(UserCouponStatus.EXPIRED)
                .expiresAt(LocalDateTime.of(2026, 2, 1, 0, 0))
                .build());

        UserCoupon updated = userCouponRepository.update(found);
        entityManager.flush();
        entityManager.clear();

        UserCoupon response = userCouponRepository.selectById(updated.getUserCouponId());
        assertThat(response.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
        assertThat(response.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @Test
    @DisplayName("이미 발급된 쿠폰이면 중복 발급 검증에서 예외 발생")
    void user_coupon_validate_not_issued_fail() {
        assertThatThrownBy(() -> userCouponRepository.validateNotIssued("user01", summerCoupon))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만료 시간이 지난 발급 쿠폰만 만료 처리")
    void user_coupon_expire_expired_user_coupons() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        UserCoupon expiredIssued = userCouponJpaRepository.save(userCoupon(
                "expired-user",
                summerCoupon,
                UserCouponStatus.ISSUED,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                null,
                now.minusMinutes(1)
        ));
        UserCoupon notExpiredIssued = userCouponJpaRepository.save(userCoupon(
                "not-expired-user",
                winterCoupon,
                UserCouponStatus.ISSUED,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                null,
                now.plusMinutes(1)
        ));
        UserCoupon usedExpired = userCouponJpaRepository.save(userCoupon(
                "used-expired-user",
                winterCoupon,
                UserCouponStatus.USED,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                now.minusDays(1),
                now.minusMinutes(1)
        ));
        entityManager.flush();
        entityManager.clear();

        long response = userCouponRepository.expireExpiredUserCoupons(now);
        entityManager.flush();
        entityManager.clear();

        assertThat(response).isEqualTo(1);
        assertThat(userCouponRepository.selectById(expiredIssued.getUserCouponId()).getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
        assertThat(userCouponRepository.selectById(notExpiredIssued.getUserCouponId()).getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCouponRepository.selectById(usedExpired.getUserCouponId()).getStatus()).isEqualTo(UserCouponStatus.USED);
    }

    private Coupon coupon(String name, String code, Integer discountValue) {
        return Coupon.builder()
                .name(name)
                .code(code)
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(discountValue)
                .minOrderAmount(10000)
                .validFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
                .validUntil(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private UserCoupon userCoupon(
            String userId,
            Coupon coupon,
            UserCouponStatus status,
            LocalDateTime issuedAt,
            LocalDateTime usedAt,
            LocalDateTime expiresAt
    ) {
        return UserCoupon.builder()
                .userId(userId)
                .coupon(coupon)
                .status(status)
                .issuedAt(issuedAt)
                .usedAt(usedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
