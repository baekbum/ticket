package dev.bum.ticket_service.jpa.payment.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPaymentSnapshotDiscount is a Querydsl query type for PaymentSnapshotDiscount
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QPaymentSnapshotDiscount extends BeanPath<PaymentSnapshotDiscount> {

    private static final long serialVersionUID = -594484279L;

    public static final QPaymentSnapshotDiscount paymentSnapshotDiscount = new QPaymentSnapshotDiscount("paymentSnapshotDiscount");

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType> couponDiscountType = createEnum("couponDiscountType", dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType.class);

    public final NumberPath<Integer> discountAmount = createNumber("discountAmount", Integer.class);

    public final StringPath discountName = createString("discountName");

    public final EnumPath<dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType> discountType = createEnum("discountType", dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType.class);

    public final NumberPath<Integer> discountValue = createNumber("discountValue", Integer.class);

    public final NumberPath<Long> userCouponId = createNumber("userCouponId", Long.class);

    public QPaymentSnapshotDiscount(String variable) {
        super(PaymentSnapshotDiscount.class, forVariable(variable));
    }

    public QPaymentSnapshotDiscount(Path<? extends PaymentSnapshotDiscount> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPaymentSnapshotDiscount(PathMetadata metadata) {
        super(PaymentSnapshotDiscount.class, metadata);
    }

}
