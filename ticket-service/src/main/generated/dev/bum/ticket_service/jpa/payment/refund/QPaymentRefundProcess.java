package dev.bum.ticket_service.jpa.payment.refund;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefundProcess is a Querydsl query type for PaymentRefundProcess
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefundProcess extends EntityPathBase<PaymentRefundProcess> {

    private static final long serialVersionUID = -451378739L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefundProcess paymentRefundProcess = new QPaymentRefundProcess("paymentRefundProcess");

    public final DateTimePath<java.time.LocalDateTime> completedAt = createDateTime("completedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath failureReason = createString("failureReason");

    public final ListPath<PaymentRefundHistory, QPaymentRefundHistory> histories = this.<PaymentRefundHistory, QPaymentRefundHistory>createList("histories", PaymentRefundHistory.class, QPaymentRefundHistory.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> lastTriedAt = createDateTime("lastTriedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> paymentRefundProcessId = createNumber("paymentRefundProcessId", Long.class);

    public final QPaymentRefund refund;

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<PaymentRefundProcessStatus> status = createEnum("status", PaymentRefundProcessStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QPaymentRefundProcess(String variable) {
        this(PaymentRefundProcess.class, forVariable(variable), INITS);
    }

    public QPaymentRefundProcess(Path<? extends PaymentRefundProcess> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefundProcess(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefundProcess(PathMetadata metadata, PathInits inits) {
        this(PaymentRefundProcess.class, metadata, inits);
    }

    public QPaymentRefundProcess(Class<? extends PaymentRefundProcess> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.refund = inits.isInitialized("refund") ? new QPaymentRefund(forProperty("refund"), inits.get("refund")) : null;
    }

}

