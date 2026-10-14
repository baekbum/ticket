package dev.bum.ticket_service.jpa.payment.refund;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefundSnapshotTicket is a Querydsl query type for PaymentRefundSnapshotTicket
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefundSnapshotTicket extends EntityPathBase<PaymentRefundSnapshotTicket> {

    private static final long serialVersionUID = 1366786258L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefundSnapshotTicket paymentRefundSnapshotTicket = new QPaymentRefundSnapshotTicket("paymentRefundSnapshotTicket");

    public final NumberPath<Long> paymentRefundSnapshotTicketId = createNumber("paymentRefundSnapshotTicketId", Long.class);

    public final QPaymentRefundSnapshot snapshot;

    public final NumberPath<Long> ticketId = createNumber("ticketId", Long.class);

    public final NumberPath<Integer> ticketPrice = createNumber("ticketPrice", Integer.class);

    public QPaymentRefundSnapshotTicket(String variable) {
        this(PaymentRefundSnapshotTicket.class, forVariable(variable), INITS);
    }

    public QPaymentRefundSnapshotTicket(Path<? extends PaymentRefundSnapshotTicket> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefundSnapshotTicket(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefundSnapshotTicket(PathMetadata metadata, PathInits inits) {
        this(PaymentRefundSnapshotTicket.class, metadata, inits);
    }

    public QPaymentRefundSnapshotTicket(Class<? extends PaymentRefundSnapshotTicket> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.snapshot = inits.isInitialized("snapshot") ? new QPaymentRefundSnapshot(forProperty("snapshot")) : null;
    }

}
