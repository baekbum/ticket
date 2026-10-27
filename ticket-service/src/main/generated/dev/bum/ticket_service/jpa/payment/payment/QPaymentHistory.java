package dev.bum.ticket_service.jpa.payment.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentHistory is a Querydsl query type for PaymentHistory
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentHistory extends EntityPathBase<PaymentHistory> {

    private static final long serialVersionUID = 41919344L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentHistory paymentHistory = new QPaymentHistory("paymentHistory");

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.PaymentStatus> afterStatus = createEnum("afterStatus", dev.bum.common.service.ticket.payment.enums.PaymentStatus.class);

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.PaymentStatus> beforeStatus = createEnum("beforeStatus", dev.bum.common.service.ticket.payment.enums.PaymentStatus.class);

    public final NumberPath<Integer> cancellationFeeAmountAfter = createNumber("cancellationFeeAmountAfter", Integer.class);

    public final EnumPath<PaymentHistoryType> eventType = createEnum("eventType", PaymentHistoryType.class);

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.PaymentMethod> method = createEnum("method", dev.bum.common.service.ticket.payment.enums.PaymentMethod.class);

    public final DateTimePath<java.time.LocalDateTime> occurredAt = createDateTime("occurredAt", java.time.LocalDateTime.class);

    public final QPayment payment;

    public final NumberPath<Long> paymentHistoryId = createNumber("paymentHistoryId", Long.class);

    public final StringPath reason = createString("reason");

    public final NumberPath<Integer> refundedAmountAfter = createNumber("refundedAmountAfter", Integer.class);

    public QPaymentHistory(String variable) {
        this(PaymentHistory.class, forVariable(variable), INITS);
    }

    public QPaymentHistory(Path<? extends PaymentHistory> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentHistory(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentHistory(PathMetadata metadata, PathInits inits) {
        this(PaymentHistory.class, metadata, inits);
    }

    public QPaymentHistory(Class<? extends PaymentHistory> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.payment = inits.isInitialized("payment") ? new QPayment(forProperty("payment"), inits.get("payment")) : null;
    }

}
