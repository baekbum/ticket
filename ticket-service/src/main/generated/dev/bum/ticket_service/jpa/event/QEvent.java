package dev.bum.ticket_service.jpa.event;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QEvent is a Querydsl query type for Event
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QEvent extends EntityPathBase<Event> {

    private static final long serialVersionUID = 117984660L;

    public static final QEvent event = new QEvent("event");

    public final StringPath artistName = createString("artistName");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath description = createString("description");

    public final DateTimePath<java.time.LocalDateTime> eventDate = createDateTime("eventDate", java.time.LocalDateTime.class);

    public final NumberPath<Long> eventId = createNumber("eventId", Long.class);

    public final ListPath<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat> seats = this.<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat>createList("seats", dev.bum.ticket_service.jpa.seat.Seat.class, dev.bum.ticket_service.jpa.seat.QSeat.class, PathInits.DIRECT2);

    public final EnumPath<dev.bum.ticket_service.enums.EventStatus> status = createEnum("status", dev.bum.ticket_service.enums.EventStatus.class);

    public final StringPath title = createString("title");

    public final NumberPath<Integer> totalSeats = createNumber("totalSeats", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath venue = createString("venue");

    public QEvent(String variable) {
        super(Event.class, forVariable(variable));
    }

    public QEvent(Path<? extends Event> path) {
        super(path.getType(), path.getMetadata());
    }

    public QEvent(PathMetadata metadata) {
        super(Event.class, metadata);
    }

}

