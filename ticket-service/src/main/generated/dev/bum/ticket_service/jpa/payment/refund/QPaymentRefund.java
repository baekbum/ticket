package dev.bum.ticket_service.jpa.payment.refund;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefund is a Querydsl query type for PaymentRefund
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefund extends EntityPathBase<PaymentRefund> {

    private static final long serialVersionUID = 1774716482L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefund paymentRefund = new QPaymentRefund("paymentRefund");

    public final dev.bum.ticket_service.jpa.payment.payment.QPayment payment;

    public final NumberPath<Long> paymentRefundId = createNumber("paymentRefundId", Long.class);

    public final QPaymentRefundSnapshot snapshot;

    public final EnumPath<PaymentRefundStatus> status = createEnum("status", PaymentRefundStatus.class);

    public QPaymentRefund(String variable) {
        this(PaymentRefund.class, forVariable(variable), INITS);
    }

    public QPaymentRefund(Path<? extends PaymentRefund> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefund(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefund(PathMetadata metadata, PathInits inits) {
        this(PaymentRefund.class, metadata, inits);
    }

    public QPaymentRefund(Class<? extends PaymentRefund> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.payment = inits.isInitialized("payment") ? new dev.bum.ticket_service.jpa.payment.payment.QPayment(forProperty("payment"), inits.get("payment")) : null;
        this.snapshot = inits.isInitialized("snapshot") ? new QPaymentRefundSnapshot(forProperty("snapshot")) : null;
    }

}
