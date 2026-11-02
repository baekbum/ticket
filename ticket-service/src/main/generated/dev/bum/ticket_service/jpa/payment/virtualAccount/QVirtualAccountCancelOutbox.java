package dev.bum.ticket_service.jpa.payment.virtualAccount;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVirtualAccountCancelOutbox is a Querydsl query type for VirtualAccountCancelOutbox
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVirtualAccountCancelOutbox extends EntityPathBase<VirtualAccountCancelOutbox> {

    private static final long serialVersionUID = 121540747L;

    public static final QVirtualAccountCancelOutbox virtualAccountCancelOutbox = new QVirtualAccountCancelOutbox("virtualAccountCancelOutbox");

    public final StringPath accountNumber = createString("accountNumber");

    public final NumberPath<Integer> amount = createNumber("amount", Integer.class);

    public final StringPath bankName = createString("bankName");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> deliveredAt = createDateTime("deliveredAt", java.time.LocalDateTime.class);

    public final StringPath lastErrorType = createString("lastErrorType");

    public final DateTimePath<java.time.LocalDateTime> nextAttemptAt = createDateTime("nextAttemptAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> outboxId = createNumber("outboxId", Long.class);

    public final StringPath paymentNo = createString("paymentNo");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<VirtualAccountCancelOutboxStatus> status = createEnum("status", VirtualAccountCancelOutboxStatus.class);

    public QVirtualAccountCancelOutbox(String variable) {
        super(VirtualAccountCancelOutbox.class, forVariable(variable));
    }

    public QVirtualAccountCancelOutbox(Path<? extends VirtualAccountCancelOutbox> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVirtualAccountCancelOutbox(PathMetadata metadata) {
        super(VirtualAccountCancelOutbox.class, metadata);
    }

}
