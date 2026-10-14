package dev.bum.ticket_service.jpa.payment.refund;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefundHistory is a Querydsl query type for PaymentRefundHistory
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefundHistory extends EntityPathBase<PaymentRefundHistory> {

    private static final long serialVersionUID = 785074162L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefundHistory paymentRefundHistory = new QPaymentRefundHistory("paymentRefundHistory");

    public final EnumPath<PaymentRefundProcessStatus> afterStatus = createEnum("afterStatus", PaymentRefundProcessStatus.class);

    public final EnumPath<PaymentRefundProcessStatus> beforeStatus = createEnum("beforeStatus", PaymentRefundProcessStatus.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final EnumPath<PaymentRefundHistoryType> eventType = createEnum("eventType", PaymentRefundHistoryType.class);

    public final StringPath failureReason = createString("failureReason");

    public final NumberPath<Long> paymentRefundHistoryId = createNumber("paymentRefundHistoryId", Long.class);

    public final QPaymentRefundProcess paymentRefundProcess;

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.PaymentStatus> paymentStatusAfter = createEnum("paymentStatusAfter", dev.bum.common.service.ticket.payment.enums.PaymentStatus.class);

    public final NumberPath<Integer> refundableAmountAfter = createNumber("refundableAmountAfter", Integer.class);

    public final NumberPath<Integer> refundedAmountAfter = createNumber("refundedAmountAfter", Integer.class);

    public final StringPath successKey = createString("successKey");

    public QPaymentRefundHistory(String variable) {
        this(PaymentRefundHistory.class, forVariable(variable), INITS);
    }

    public QPaymentRefundHistory(Path<? extends PaymentRefundHistory> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefundHistory(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefundHistory(PathMetadata metadata, PathInits inits) {
        this(PaymentRefundHistory.class, metadata, inits);
    }

    public QPaymentRefundHistory(Class<? extends PaymentRefundHistory> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.paymentRefundProcess = inits.isInitialized("paymentRefundProcess") ? new QPaymentRefundProcess(forProperty("paymentRefundProcess"), inits.get("paymentRefundProcess")) : null;
    }

}

