package dev.bum.ticket_service.jpa.coupon.userCoupon;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.QCoupon;
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
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public UserCoupon insert(UserCoupon userCoupon) {
        return jpaRepository.save(userCoupon);
    }

    @Override
    public UserCoupon update(UserCoupon userCoupon) {
        return jpaRepository.saveAndFlush(userCoupon);
    }

    @Override
    public UserCoupon selectById(Long userCouponId) {
        return jpaRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("User coupon not found."));
    }

    @Override
    public List<UserCoupon> selectByUserId(String userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public Page<UserCoupon> selectByCond(UserCouponCondRequest cond, Pageable pageable) {
        QUserCoupon userCoupon = QUserCoupon.userCoupon;
        QCoupon coupon = QCoupon.coupon;

        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            PathBuilder<UserCoupon> entityPath = new PathBuilder<>(UserCoupon.class, "userCoupon");
            orderSpecifiers.add(new OrderSpecifier<>(direction, entityPath.getComparable(order.getProperty(), Comparable.class)));
        });

        List<UserCoupon> content = queryFactory
                .selectFrom(userCoupon)
                .join(userCoupon.coupon, coupon).fetchJoin()
                .where(
                        userIdLike(cond.getUserId()),
                        couponNameLike(cond.getCouponName()),
                        couponCodeLike(cond.getCouponCode()),
                        discountTypeEq(cond.getDiscountType()),
                        statusEq(cond.getStatus()),
                        issuedAtDateEq(cond.getIssuedAt()),
                        expiresAtDateEq(cond.getExpiresAt()),
                        usedAtDateEq(cond.getUsedAt())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new))
                .fetch();

        Long total = queryFactory
                .select(userCoupon.count())
                .from(userCoupon)
                .join(userCoupon.coupon, coupon)
                .where(
                        userIdLike(cond.getUserId()),
                        couponNameLike(cond.getCouponName()),
                        couponCodeLike(cond.getCouponCode()),
                        discountTypeEq(cond.getDiscountType()),
                        statusEq(cond.getStatus()),
                        issuedAtDateEq(cond.getIssuedAt()),
                        expiresAtDateEq(cond.getExpiresAt()),
                        usedAtDateEq(cond.getUsedAt())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long expireExpiredUserCoupons(LocalDateTime now) {
        QUserCoupon userCoupon = QUserCoupon.userCoupon;

        return queryFactory
                .update(userCoupon)
                .set(userCoupon.status, UserCouponStatus.EXPIRED)
                .set(userCoupon.updatedAt, now)
                .where(
                        userCoupon.status.eq(UserCouponStatus.ISSUED),
                        userCoupon.expiresAt.isNotNull(),
                        userCoupon.expiresAt.lt(now)
                )
                .execute();
    }

    @Override
    public void validateNotIssued(String userId, Coupon coupon) {
        jpaRepository.findByUserIdAndCoupon(userId, coupon)
                .ifPresent(userCoupon -> {
                    throw new IllegalArgumentException("Already issued coupon.");
                });
    }

    private BooleanExpression userIdLike(String userId) {
        return StringUtils.hasText(userId) ? QUserCoupon.userCoupon.userId.like("%" + userId + "%") : null;
    }

    private BooleanExpression couponNameLike(String couponName) {
        return StringUtils.hasText(couponName) ? QCoupon.coupon.name.like("%" + couponName + "%") : null;
    }

    private BooleanExpression couponCodeLike(String couponCode) {
        return StringUtils.hasText(couponCode) ? QCoupon.coupon.code.like("%" + couponCode + "%") : null;
    }

    private BooleanExpression discountTypeEq(CouponDiscountType discountType) {
        return discountType != null ? QCoupon.coupon.discountType.eq(discountType) : null;
    }

    private BooleanExpression statusEq(UserCouponStatus status) {
        return status != null ? QUserCoupon.userCoupon.status.eq(status) : null;
    }

    private BooleanExpression issuedAtDateEq(LocalDate issuedAt) {
        return issuedAt != null
                ? QUserCoupon.userCoupon.issuedAt.between(issuedAt.atStartOfDay(), issuedAt.atTime(LocalTime.MAX))
                : null;
    }

    private BooleanExpression expiresAtDateEq(LocalDate expiresAt) {
        return expiresAt != null
                ? QUserCoupon.userCoupon.expiresAt.between(expiresAt.atStartOfDay(), expiresAt.atTime(LocalTime.MAX))
                : null;
    }

    private BooleanExpression usedAtDateEq(LocalDate usedAt) {
        return usedAt != null
                ? QUserCoupon.userCoupon.usedAt.between(usedAt.atStartOfDay(), usedAt.atTime(LocalTime.MAX))
                : null;
    }
}
