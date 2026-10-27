package dev.bum.ticket_service.jpa.payment.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPaymentSnapshotTicket is a Querydsl query type for PaymentSnapshotTicket
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QPaymentSnapshotTicket extends BeanPath<PaymentSnapshotTicket> {

    private static final long serialVersionUID = -687154284L;

    public static final QPaymentSnapshotTicket paymentSnapshotTicket = new QPaymentSnapshotTicket("paymentSnapshotTicket");

    public final NumberPath<Long> ticketId = createNumber("ticketId", Long.class);

    public final NumberPath<Integer> ticketPrice = createNumber("ticketPrice", Integer.class);

    public QPaymentSnapshotTicket(String variable) {
        super(PaymentSnapshotTicket.class, forVariable(variable));
    }

    public QPaymentSnapshotTicket(Path<? extends PaymentSnapshotTicket> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPaymentSnapshotTicket(PathMetadata metadata) {
        super(PaymentSnapshotTicket.class, metadata);
    }

}
