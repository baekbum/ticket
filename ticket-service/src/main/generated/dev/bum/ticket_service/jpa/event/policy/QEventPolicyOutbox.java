package dev.bum.ticket_service.jpa.event.policy;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QEventPolicyOutbox is a Querydsl query type for EventPolicyOutbox
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QEventPolicyOutbox extends EntityPathBase<EventPolicyOutbox> {

    private static final long serialVersionUID = -1611829229L;

    public static final QEventPolicyOutbox eventPolicyOutbox = new QEventPolicyOutbox("eventPolicyOutbox");

    public final NumberPath<Integer> admissionSize = createNumber("admissionSize", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> eventDateTime = createNumber("eventDateTime", Long.class);

    public final NumberPath<Long> eventId = createNumber("eventId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> nextAttemptAt = createDateTime("nextAttemptAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> outboxId = createNumber("outboxId", Long.class);

    public final BooleanPath published = createBoolean("published");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final NumberPath<Long> saleEndAt = createNumber("saleEndAt", Long.class);

    public final NumberPath<Long> saleStartAt = createNumber("saleStartAt", Long.class);

    public final StringPath status = createString("status");

    public QEventPolicyOutbox(String variable) {
        super(EventPolicyOutbox.class, forVariable(variable));
    }

    public QEventPolicyOutbox(Path<? extends EventPolicyOutbox> path) {
        super(path.getType(), path.getMetadata());
    }

    public QEventPolicyOutbox(PathMetadata metadata) {
        super(EventPolicyOutbox.class, metadata);
    }

}
