package dev.bum.ticket_service.jpa.reservation.reservationDiscount;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReservationDiscount is a Querydsl query type for ReservationDiscount
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReservationDiscount extends EntityPathBase<ReservationDiscount> {

    private static final long serialVersionUID = -2071287784L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QReservationDiscount reservationDiscount = new QReservationDiscount("reservationDiscount");

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType> couponDiscountType = createEnum("couponDiscountType", dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> discountAmount = createNumber("discountAmount", Integer.class);

    public final StringPath discountName = createString("discountName");

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType> discountType = createEnum("discountType", dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType.class);

    public final NumberPath<Integer> discountValue = createNumber("discountValue", Integer.class);

    public final dev.bum.ticket_service.jpa.reservation.reservation.QReservation reservation;

    public final NumberPath<Long> reservationDiscountId = createNumber("reservationDiscountId", Long.class);

    public final dev.bum.ticket_service.jpa.coupon.userCoupon.QUserCoupon userCoupon;

    public QReservationDiscount(String variable) {
        this(ReservationDiscount.class, forVariable(variable), INITS);
    }

    public QReservationDiscount(Path<? extends ReservationDiscount> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QReservationDiscount(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QReservationDiscount(PathMetadata metadata, PathInits inits) {
        this(ReservationDiscount.class, metadata, inits);
    }

    public QReservationDiscount(Class<? extends ReservationDiscount> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.reservation = inits.isInitialized("reservation") ? new dev.bum.ticket_service.jpa.reservation.reservation.QReservation(forProperty("reservation"), inits.get("reservation")) : null;
        this.userCoupon = inits.isInitialized("userCoupon") ? new dev.bum.ticket_service.jpa.coupon.userCoupon.QUserCoupon(forProperty("userCoupon"), inits.get("userCoupon")) : null;
    }

}

