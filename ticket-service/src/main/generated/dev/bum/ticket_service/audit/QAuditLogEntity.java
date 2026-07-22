package dev.bum.ticket_service.audit;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QAuditLogEntity is a Querydsl query type for AuditLogEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAuditLogEntity extends EntityPathBase<AuditLogEntity> {

    private static final long serialVersionUID = -1496029986L;

    public static final QAuditLogEntity auditLogEntity = new QAuditLogEntity("auditLogEntity");

    public final StringPath action = createString("action");

    public final StringPath actorId = createString("actorId");

    public final StringPath actorName = createString("actorName");

    public final StringPath actorType = createString("actorType");

    public final MapPath<String, Object, SimplePath<Object>> afterData = this.<String, Object, SimplePath<Object>>createMap("afterData", String.class, Object.class, SimplePath.class);

    public final MapPath<String, Object, SimplePath<Object>> beforeData = this.<String, Object, SimplePath<Object>>createMap("beforeData", String.class, Object.class, SimplePath.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath ipAddress = createString("ipAddress");

    public final MapPath<String, Object, SimplePath<Object>> metadata = this.<String, Object, SimplePath<Object>>createMap("metadata", String.class, Object.class, SimplePath.class);

    public final DateTimePath<java.time.LocalDateTime> occurredAt = createDateTime("occurredAt", java.time.LocalDateTime.class);

    public final StringPath reason = createString("reason");

    public final StringPath requestId = createString("requestId");

    public final StringPath result = createString("result");

    public final StringPath serviceName = createString("serviceName");

    public final StringPath targetId = createString("targetId");

    public final StringPath targetType = createString("targetType");

    public final StringPath traceId = createString("traceId");

    public final StringPath userAgent = createString("userAgent");

    public QAuditLogEntity(String variable) {
        super(AuditLogEntity.class, forVariable(variable));
    }

    public QAuditLogEntity(Path<? extends AuditLogEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QAuditLogEntity(PathMetadata metadata) {
        super(AuditLogEntity.class, metadata);
    }

}

