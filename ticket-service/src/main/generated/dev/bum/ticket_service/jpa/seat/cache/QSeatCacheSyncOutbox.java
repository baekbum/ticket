package dev.bum.ticket_service.jpa.seat.cache;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSeatCacheSyncOutbox is a Querydsl query type for SeatCacheSyncOutbox
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSeatCacheSyncOutbox extends EntityPathBase<SeatCacheSyncOutbox> {

    private static final long serialVersionUID = -1644590798L;

    public static final QSeatCacheSyncOutbox seatCacheSyncOutbox = new QSeatCacheSyncOutbox("seatCacheSyncOutbox");

    public final DateTimePath<java.time.LocalDateTime> appliedAt = createDateTime("appliedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath lastErrorType = createString("lastErrorType");

    public final StringPath lockValue = createString("lockValue");

    public final DateTimePath<java.time.LocalDateTime> nextAttemptAt = createDateTime("nextAttemptAt", java.time.LocalDateTime.class);

    public final StringPath operation = createString("operation");

    public final NumberPath<Long> outboxId = createNumber("outboxId", Long.class);

    public final StringPath redisKey = createString("redisKey");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final NumberPath<Long> seatId = createNumber("seatId", Long.class);

    public final EnumPath<SeatCacheSyncOutboxStatus> status = createEnum("status", SeatCacheSyncOutboxStatus.class);

    public QSeatCacheSyncOutbox(String variable) {
        super(SeatCacheSyncOutbox.class, forVariable(variable));
    }

    public QSeatCacheSyncOutbox(Path<? extends SeatCacheSyncOutbox> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSeatCacheSyncOutbox(PathMetadata metadata) {
        super(SeatCacheSyncOutbox.class, metadata);
    }

}
