package dev.bum.ticket_service.jpa.checkout;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QCheckoutAttempt is a Querydsl query type for CheckoutAttempt
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QCheckoutAttempt extends EntityPathBase<CheckoutAttempt> {

    private static final long serialVersionUID = -760801999L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QCheckoutAttempt checkoutAttempt = new QCheckoutAttempt("checkoutAttempt");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> eventId = createNumber("eventId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final StringPath orderId = createString("orderId");

    public final dev.bum.ticket_service.jpa.payment.QPayment payment;

    public final StringPath paymentNo = createString("paymentNo");

    public final EnumPath<CheckoutAttemptStatus> status = createEnum("status", CheckoutAttemptStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath userId = createString("userId");

    public QCheckoutAttempt(String variable) {
        this(CheckoutAttempt.class, forVariable(variable), INITS);
    }

    public QCheckoutAttempt(Path<? extends CheckoutAttempt> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QCheckoutAttempt(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QCheckoutAttempt(PathMetadata metadata, PathInits inits) {
        this(CheckoutAttempt.class, metadata, inits);
    }

    public QCheckoutAttempt(Class<? extends CheckoutAttempt> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.payment = inits.isInitialized("payment") ? new dev.bum.ticket_service.jpa.payment.QPayment(forProperty("payment"), inits.get("payment")) : null;
    }

}

