package dev.bum.ticket_service.jpa.coupon.userCoupon;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QUserCoupon is a Querydsl query type for UserCoupon
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUserCoupon extends EntityPathBase<UserCoupon> {

    private static final long serialVersionUID = -2008088392L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QUserCoupon userCoupon = new QUserCoupon("userCoupon");

    public final dev.bum.ticket_service.jpa.coupon.coupon.QCoupon coupon;

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> issuedAt = createDateTime("issuedAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus> status = createEnum("status", dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> usedAt = createDateTime("usedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> userCouponId = createNumber("userCouponId", Long.class);

    public final StringPath userId = createString("userId");

    public QUserCoupon(String variable) {
        this(UserCoupon.class, forVariable(variable), INITS);
    }

    public QUserCoupon(Path<? extends UserCoupon> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QUserCoupon(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QUserCoupon(PathMetadata metadata, PathInits inits) {
        this(UserCoupon.class, metadata, inits);
    }

    public QUserCoupon(Class<? extends UserCoupon> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.coupon = inits.isInitialized("coupon") ? new dev.bum.ticket_service.jpa.coupon.coupon.QCoupon(forProperty("coupon")) : null;
    }

}

