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

    public final NumberPath<Integer> ageLimit = createNumber("ageLimit", Integer.class);

    public final ListPath<dev.bum.ticket_service.jpa.area.Area, dev.bum.ticket_service.jpa.area.QArea> areas = this.<dev.bum.ticket_service.jpa.area.Area, dev.bum.ticket_service.jpa.area.QArea>createList("areas", dev.bum.ticket_service.jpa.area.Area.class, dev.bum.ticket_service.jpa.area.QArea.class, PathInits.DIRECT2);

    public final StringPath artistName = createString("artistName");

    public final NumberPath<Integer> availableSeats = createNumber("availableSeats", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> cancelDeadlineAt = createDateTime("cancelDeadlineAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath description = createString("description");

    public final DateTimePath<java.time.LocalDateTime> eventDateTime = createDateTime("eventDateTime", java.time.LocalDateTime.class);

    public final NumberPath<Long> eventId = createNumber("eventId", Long.class);

    public final NumberPath<Integer> maxTicketsPerPerson = createNumber("maxTicketsPerPerson", Integer.class);

    public final StringPath posterUrl = createString("posterUrl");

    public final NumberPath<Integer> runningMinutes = createNumber("runningMinutes", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> saleEndAt = createDateTime("saleEndAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> saleStartAt = createDateTime("saleStartAt", java.time.LocalDateTime.class);

    public final ListPath<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat> seats = this.<dev.bum.ticket_service.jpa.seat.Seat, dev.bum.ticket_service.jpa.seat.QSeat>createList("seats", dev.bum.ticket_service.jpa.seat.Seat.class, dev.bum.ticket_service.jpa.seat.QSeat.class, PathInits.DIRECT2);

    public final EnumPath<dev.bum.common.service.ticket.event.enums.EventStatus> status = createEnum("status", dev.bum.common.service.ticket.event.enums.EventStatus.class);

    public final StringPath title = createString("title");

    public final NumberPath<Integer> totalSeats = createNumber("totalSeats", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath venue = createString("venue");

    public final StringPath venueAddress = createString("venueAddress");

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

