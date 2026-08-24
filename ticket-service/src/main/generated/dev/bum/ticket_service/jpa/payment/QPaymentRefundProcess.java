package dev.bum.ticket_service.jpa.payment;

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

    private static final long serialVersionUID = 1471969483L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefundProcess paymentRefundProcess = new QPaymentRefundProcess("paymentRefundProcess");

    public final DateTimePath<java.time.LocalDateTime> completedAt = createDateTime("completedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath failureReason = createString("failureReason");

    public final BooleanPath fullCancellation = createBoolean("fullCancellation");

    public final DateTimePath<java.time.LocalDateTime> lastTriedAt = createDateTime("lastTriedAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.PaymentMethod> method = createEnum("method", dev.bum.common.service.ticket.payment.enums.PaymentMethod.class);

    public final QPayment payment;

    public final StringPath paymentNo = createString("paymentNo");

    public final NumberPath<Long> paymentRefundProcessId = createNumber("paymentRefundProcessId", Long.class);

    public final StringPath refundAccountHolder = createString("refundAccountHolder");

    public final StringPath refundAccountNumberMasked = createString("refundAccountNumberMasked");

    public final NumberPath<Integer> refundAmount = createNumber("refundAmount", Integer.class);

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.BankCompany> refundBankCompany = createEnum("refundBankCompany", dev.bum.common.service.ticket.payment.enums.BankCompany.class);

    public final dev.bum.ticket_service.jpa.reservation.reservation.QReservation reservation;

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final StringPath selectedTicketIds = createString("selectedTicketIds");

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
        this.payment = inits.isInitialized("payment") ? new QPayment(forProperty("payment"), inits.get("payment")) : null;
        this.reservation = inits.isInitialized("reservation") ? new dev.bum.ticket_service.jpa.reservation.reservation.QReservation(forProperty("reservation"), inits.get("reservation")) : null;
    }

}

