package dev.bum.ticket_service.jpa.layout;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QEventLayout is a Querydsl query type for EventLayout
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QEventLayout extends EntityPathBase<EventLayout> {

    private static final long serialVersionUID = -5404078L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QEventLayout eventLayout = new QEventLayout("eventLayout");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final dev.bum.ticket_service.jpa.event.QEvent event;

    public final NumberPath<Long> layoutId = createNumber("layoutId", Long.class);

    public final StringPath originalFileName = createString("originalFileName");

    public final StringPath svgText = createString("svgText");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QEventLayout(String variable) {
        this(EventLayout.class, forVariable(variable), INITS);
    }

    public QEventLayout(Path<? extends EventLayout> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QEventLayout(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QEventLayout(PathMetadata metadata, PathInits inits) {
        this(EventLayout.class, metadata, inits);
    }

    public QEventLayout(Class<? extends EventLayout> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.event = inits.isInitialized("event") ? new dev.bum.ticket_service.jpa.event.QEvent(forProperty("event")) : null;
    }

}

