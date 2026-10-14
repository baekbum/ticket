package dev.bum.ticket_service.jpa.payment.refund;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefundSnapshot is a Querydsl query type for PaymentRefundSnapshot
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefundSnapshot extends EntityPathBase<PaymentRefundSnapshot> {

    private static final long serialVersionUID = 182181414L;

    public static final QPaymentRefundSnapshot paymentRefundSnapshot = new QPaymentRefundSnapshot("paymentRefundSnapshot");

    public final NumberPath<Integer> cancellationFeeAmount = createNumber("cancellationFeeAmount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> capturedAt = createDateTime("capturedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> deliveryRefundAmount = createNumber("deliveryRefundAmount", Integer.class);

    public final NumberPath<Integer> discountAmount = createNumber("discountAmount", Integer.class);

    public final BooleanPath fullCancellation = createBoolean("fullCancellation");

    public final NumberPath<Long> paymentRefundSnapshotId = createNumber("paymentRefundSnapshotId", Long.class);

    public final StringPath refundAccountHolder = createString("refundAccountHolder");

    public final StringPath refundAccountNumberHash = createString("refundAccountNumberHash");

    public final StringPath refundAccountNumberMasked = createString("refundAccountNumberMasked");

    public final NumberPath<Integer> refundAmount = createNumber("refundAmount", Integer.class);

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.BankCompany> refundBankCompany = createEnum("refundBankCompany", dev.bum.common.service.ticket.payment.enums.BankCompany.class);

    public final NumberPath<Integer> ticketAmount = createNumber("ticketAmount", Integer.class);

    public final ListPath<PaymentRefundSnapshotTicket, QPaymentRefundSnapshotTicket> tickets = this.<PaymentRefundSnapshotTicket, QPaymentRefundSnapshotTicket>createList("tickets", PaymentRefundSnapshotTicket.class, QPaymentRefundSnapshotTicket.class, PathInits.DIRECT2);

    public QPaymentRefundSnapshot(String variable) {
        super(PaymentRefundSnapshot.class, forVariable(variable));
    }

    public QPaymentRefundSnapshot(Path<? extends PaymentRefundSnapshot> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPaymentRefundSnapshot(PathMetadata metadata) {
        super(PaymentRefundSnapshot.class, metadata);
    }

}
