package dev.bum.ticket_service.jpa.seat.cache;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSeatCacheSyncFailure is a Querydsl query type for SeatCacheSyncFailure
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSeatCacheSyncFailure extends EntityPathBase<SeatCacheSyncFailure> {

    private static final long serialVersionUID = 577256117L;

    public static final QSeatCacheSyncFailure seatCacheSyncFailure = new QSeatCacheSyncFailure("seatCacheSyncFailure");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath failureMessage = createString("failureMessage");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath keyPrefix = createString("keyPrefix");

    public final DateTimePath<java.time.LocalDateTime> lastFailedAt = createDateTime("lastFailedAt", java.time.LocalDateTime.class);

    public final StringPath operation = createString("operation");

    public final StringPath redisKeys = createString("redisKeys");

    public final DateTimePath<java.time.LocalDateTime> resolvedAt = createDateTime("resolvedAt", java.time.LocalDateTime.class);

    public final StringPath resolvedMessage = createString("resolvedMessage");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<SeatCacheSyncFailureStatus> status = createEnum("status", SeatCacheSyncFailureStatus.class);

    public final StringPath targetValue = createString("targetValue");

    public QSeatCacheSyncFailure(String variable) {
        super(SeatCacheSyncFailure.class, forVariable(variable));
    }

    public QSeatCacheSyncFailure(Path<? extends SeatCacheSyncFailure> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSeatCacheSyncFailure(PathMetadata metadata) {
        super(SeatCacheSyncFailure.class, metadata);
    }

}

