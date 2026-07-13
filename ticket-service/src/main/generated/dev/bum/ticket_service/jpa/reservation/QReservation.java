package dev.bum.ticket_service.jpa.reservation;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReservation is a Querydsl query type for Reservation
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReservation extends EntityPathBase<Reservation> {

    private static final long serialVersionUID = 1083451128L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QReservation reservation = new QReservation("reservation");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final dev.bum.ticket_service.jpa.event.QEvent event;

    public final StringPath orderId = createString("orderId");

    public final NumberPath<Long> reservationId = createNumber("reservationId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> reservedAt = createDateTime("reservedAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.common.service.ticket.reservation.enums.ReservationStatus> status = createEnum("status", dev.bum.common.service.ticket.reservation.enums.ReservationStatus.class);

    public final ListPath<dev.bum.ticket_service.jpa.ticket.Ticket, dev.bum.ticket_service.jpa.ticket.QTicket> tickets = this.<dev.bum.ticket_service.jpa.ticket.Ticket, dev.bum.ticket_service.jpa.ticket.QTicket>createList("tickets", dev.bum.ticket_service.jpa.ticket.Ticket.class, dev.bum.ticket_service.jpa.ticket.QTicket.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath userId = createString("userId");

    public QReservation(String variable) {
        this(Reservation.class, forVariable(variable), INITS);
    }

    public QReservation(Path<? extends Reservation> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QReservation(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QReservation(PathMetadata metadata, PathInits inits) {
        this(Reservation.class, metadata, inits);
    }

    public QReservation(Class<? extends Reservation> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.event = inits.isInitialized("event") ? new dev.bum.ticket_service.jpa.event.QEvent(forProperty("event")) : null;
    }

}

