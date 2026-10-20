package dev.bum.user_service.jpa.outbox;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QUserOutboxEvent is a Querydsl query type for UserOutboxEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUserOutboxEvent extends EntityPathBase<UserOutboxEvent> {

    private static final long serialVersionUID = 621085492L;

    public static final QUserOutboxEvent userOutboxEvent = new QUserOutboxEvent("userOutboxEvent");

    public final NumberPath<Integer> attemptCount = createNumber("attemptCount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath lastError = createString("lastError");

    public final DateTimePath<java.time.LocalDateTime> nextAttemptAt = createDateTime("nextAttemptAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> outboxId = createNumber("outboxId", Long.class);

    public final StringPath payload = createString("payload");

    public final DateTimePath<java.time.LocalDateTime> publishedAt = createDateTime("publishedAt", java.time.LocalDateTime.class);

    public final EnumPath<UserOutboxStatus> status = createEnum("status", UserOutboxStatus.class);

    public final StringPath topic = createString("topic");

    public final StringPath userId = createString("userId");

    public QUserOutboxEvent(String variable) {
        super(UserOutboxEvent.class, forVariable(variable));
    }

    public QUserOutboxEvent(Path<? extends UserOutboxEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QUserOutboxEvent(PathMetadata metadata) {
        super(UserOutboxEvent.class, metadata);
    }

}
