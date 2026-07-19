package dev.bum.ticket_service.jpa.area;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QArea is a Querydsl query type for Area
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QArea extends EntityPathBase<Area> {

    private static final long serialVersionUID = -1183102884L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QArea area = new QArea("area");

    public final NumberPath<Long> areaId = createNumber("areaId", Long.class);

    public final StringPath areaName = createString("areaName");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final dev.bum.ticket_service.jpa.event.event.QEvent event;

    public final EnumPath<dev.bum.common.service.ticket.seat.enums.SeatGrade> grade = createEnum("grade", dev.bum.common.service.ticket.seat.enums.SeatGrade.class);

    public final StringPath layoutKey = createString("layoutKey");

    public final NumberPath<Integer> price = createNumber("price", Integer.class);

    public final ListPath<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat> seats = this.<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat>createList("seats", dev.bum.ticket_service.jpa.seat.Seat.class, dev.bum.ticket_service.jpa.seat.QSeat.class, PathInits.DIRECT2);

    public final EnumPath<dev.bum.common.service.ticket.area.enums.AreaStatus> status = createEnum("status", dev.bum.common.service.ticket.area.enums.AreaStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QArea(String variable) {
        this(Area.class, forVariable(variable), INITS);
    }

    public QArea(Path<? extends Area> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QArea(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QArea(PathMetadata metadata, PathInits inits) {
        this(Area.class, metadata, inits);
    }

    public QArea(Class<? extends Area> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.event = inits.isInitialized("event") ? new dev.bum.ticket_service.jpa.event.event.QEvent(forProperty("event")) : null;
    }

}

