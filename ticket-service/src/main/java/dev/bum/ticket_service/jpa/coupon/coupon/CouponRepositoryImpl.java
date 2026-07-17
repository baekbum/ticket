package dev.bum.ticket_service.jpa.coupon.coupon;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Coupon insert(Coupon coupon) {
        return jpaRepository.save(coupon);
    }

    @Override
    public Coupon update(Coupon coupon) {
        return jpaRepository.saveAndFlush(coupon);
    }

    @Override
    public Coupon selectById(Long couponId) {
        return jpaRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정보가 존재하지 않습니다."));
    }

    @Override
    public Page<Coupon> selectByCond(CouponCondRequest cond, Pageable pageable) {
        QCoupon coupon = QCoupon.coupon;

        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            PathBuilder<Coupon> entityPath = new PathBuilder<>(Coupon.class, "coupon");
            orderSpecifiers.add(new OrderSpecifier<>(direction, entityPath.getComparable(order.getProperty(), Comparable.class)));
        });

        List<Coupon> content = queryFactory
                .selectFrom(coupon)
                .where(
                        nameLike(cond.getName()),
                        codeLike(cond.getCode()),
                        discountTypeEq(cond.getDiscountType()),
                        validFromDateEq(cond.getValidFrom()),
                        validUntilDateEq(cond.getValidUntil()),
                        validDaysAfterIssueEq(cond.getValidDaysAfterIssue()),
                        statusEq(cond.getStatus())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new))
                .fetch();

        Long total = queryFactory
                .select(coupon.count())
                .from(coupon)
                .where(
                        nameLike(cond.getName()),
                        codeLike(cond.getCode()),
                        discountTypeEq(cond.getDiscountType()),
                        validFromDateEq(cond.getValidFrom()),
                        validUntilDateEq(cond.getValidUntil()),
                        validDaysAfterIssueEq(cond.getValidDaysAfterIssue()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Coupon> selectDownloadableCoupons(LocalDateTime now) {
        QCoupon coupon = QCoupon.coupon;

        return queryFactory
                .selectFrom(coupon)
                .where(
                        coupon.status.eq(CouponStatus.ACTIVE),
                        coupon.validFrom.isNull().or(coupon.validFrom.loe(now)),
                        coupon.validUntil.isNull().or(coupon.validUntil.goe(now))
                )
                .orderBy(coupon.couponId.desc())
                .fetch();
    }

    @Override
    public long expireExpiredCoupons(LocalDateTime now) {
        QCoupon coupon = QCoupon.coupon;

        return queryFactory
                .update(coupon)
                .set(coupon.status, CouponStatus.EXPIRED)
                .set(coupon.updatedAt, now)
                .where(
                        coupon.status.eq(CouponStatus.ACTIVE),
                        coupon.validUntil.isNotNull(),
                        coupon.validUntil.lt(now)
                )
                .execute();
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public boolean existsByCodeExceptId(String code, Long couponId) {
        return jpaRepository.existsByCodeAndCouponIdNot(code, couponId);
    }

    private BooleanExpression nameLike(String name) {
        return StringUtils.hasText(name) ? QCoupon.coupon.name.like("%" + name + "%") : null;
    }

    private BooleanExpression codeLike(String code) {
        return StringUtils.hasText(code) ? QCoupon.coupon.code.like("%" + code + "%") : null;
    }

    private BooleanExpression discountTypeEq(CouponDiscountType discountType) {
        return discountType != null ? QCoupon.coupon.discountType.eq(discountType) : null;
    }

    private BooleanExpression validFromDateEq(LocalDate validFrom) {
        return validFrom != null
                ? QCoupon.coupon.validFrom.between(validFrom.atStartOfDay(), validFrom.atTime(LocalTime.MAX))
                : null;
    }

    private BooleanExpression validUntilDateEq(LocalDate validUntil) {
        return validUntil != null
                ? QCoupon.coupon.validUntil.between(validUntil.atStartOfDay(), validUntil.atTime(LocalTime.MAX))
                : null;
    }

    private BooleanExpression validDaysAfterIssueEq(Integer validDaysAfterIssue) {
        return validDaysAfterIssue != null ? QCoupon.coupon.validDaysAfterIssue.eq(validDaysAfterIssue) : null;
    }

    private BooleanExpression statusEq(CouponStatus status) {
        return status != null ? QCoupon.coupon.status.eq(status) : null;
    }
}
