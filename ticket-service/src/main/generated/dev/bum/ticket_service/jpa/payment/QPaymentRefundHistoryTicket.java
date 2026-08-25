package dev.bum.ticket_service.jpa.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefundHistoryTicket is a Querydsl query type for PaymentRefundHistoryTicket
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefundHistoryTicket extends EntityPathBase<PaymentRefundHistoryTicket> {

    private static final long serialVersionUID = -2119327204L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefundHistoryTicket paymentRefundHistoryTicket = new QPaymentRefundHistoryTicket("paymentRefundHistoryTicket");

    public final QPaymentRefundHistory paymentRefundHistory;

    public final NumberPath<Long> paymentRefundHistoryTicketId = createNumber("paymentRefundHistoryTicketId", Long.class);

    public final dev.bum.ticket_service.jpa.ticket.QTicket ticket;

    public final NumberPath<Integer> ticketPrice = createNumber("ticketPrice", Integer.class);

    public QPaymentRefundHistoryTicket(String variable) {
        this(PaymentRefundHistoryTicket.class, forVariable(variable), INITS);
    }

    public QPaymentRefundHistoryTicket(Path<? extends PaymentRefundHistoryTicket> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefundHistoryTicket(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefundHistoryTicket(PathMetadata metadata, PathInits inits) {
        this(PaymentRefundHistoryTicket.class, metadata, inits);
    }

    public QPaymentRefundHistoryTicket(Class<? extends PaymentRefundHistoryTicket> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.paymentRefundHistory = inits.isInitialized("paymentRefundHistory") ? new QPaymentRefundHistory(forProperty("paymentRefundHistory"), inits.get("paymentRefundHistory")) : null;
        this.ticket = inits.isInitialized("ticket") ? new dev.bum.ticket_service.jpa.ticket.QTicket(forProperty("ticket"), inits.get("ticket")) : null;
    }

}

