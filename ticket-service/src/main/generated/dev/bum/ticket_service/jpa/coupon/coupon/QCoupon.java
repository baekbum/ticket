package dev.bum.ticket_service.jpa.coupon.coupon;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QCoupon is a Querydsl query type for Coupon
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QCoupon extends EntityPathBase<Coupon> {

    private static final long serialVersionUID = -1277999208L;

    public static final QCoupon coupon = new QCoupon("coupon");

    public final StringPath code = createString("code");

    public final NumberPath<Long> couponId = createNumber("couponId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType> discountType = createEnum("discountType", dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType.class);

    public final NumberPath<Integer> discountValue = createNumber("discountValue", Integer.class);

    public final NumberPath<Integer> maxDiscountAmount = createNumber("maxDiscountAmount", Integer.class);

    public final NumberPath<Integer> minOrderAmount = createNumber("minOrderAmount", Integer.class);

    public final StringPath name = createString("name");

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus> status = createEnum("status", dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> validDaysAfterIssue = createNumber("validDaysAfterIssue", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> validFrom = createDateTime("validFrom", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> validUntil = createDateTime("validUntil", java.time.LocalDateTime.class);

    public QCoupon(String variable) {
        super(Coupon.class, forVariable(variable));
    }

    public QCoupon(Path<? extends Coupon> path) {
        super(path.getType(), path.getMetadata());
    }

    public QCoupon(PathMetadata metadata) {
        super(Coupon.class, metadata);
    }

}

