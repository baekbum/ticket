package dev.bum.ticket_service.jpa.payment.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentSnapshot is a Querydsl query type for PaymentSnapshot
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentSnapshot extends EntityPathBase<PaymentSnapshot> {

    private static final long serialVersionUID = -1380781464L;

    public static final QPaymentSnapshot paymentSnapshot = new QPaymentSnapshot("paymentSnapshot");

    public final NumberPath<Integer> amount = createNumber("amount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> capturedAt = createDateTime("capturedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> deliveryFeeAmount = createNumber("deliveryFeeAmount", Integer.class);

    public final ListPath<PaymentSnapshotDiscount, QPaymentSnapshotDiscount> discounts = this.<PaymentSnapshotDiscount, QPaymentSnapshotDiscount>createList("discounts", PaymentSnapshotDiscount.class, QPaymentSnapshotDiscount.class, PathInits.DIRECT2);

    public final NumberPath<Long> paymentSnapshotId = createNumber("paymentSnapshotId", Long.class);

    public final NumberPath<Integer> reservationFeeAmount = createNumber("reservationFeeAmount", Integer.class);

    public final ListPath<PaymentSnapshotTicket, QPaymentSnapshotTicket> tickets = this.<PaymentSnapshotTicket, QPaymentSnapshotTicket>createList("tickets", PaymentSnapshotTicket.class, QPaymentSnapshotTicket.class, PathInits.DIRECT2);

    public QPaymentSnapshot(String variable) {
        super(PaymentSnapshot.class, forVariable(variable));
    }

    public QPaymentSnapshot(Path<? extends PaymentSnapshot> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPaymentSnapshot(PathMetadata metadata) {
        super(PaymentSnapshot.class, metadata);
    }

}
