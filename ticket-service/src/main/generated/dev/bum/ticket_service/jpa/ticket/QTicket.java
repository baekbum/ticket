package dev.bum.ticket_service.jpa.ticket;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QTicket is a Querydsl query type for Ticket
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QTicket extends EntityPathBase<Ticket> {

    private static final long serialVersionUID = -1304661956L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QTicket ticket = new QTicket("ticket");

    public final dev.bum.ticket_service.jpa.event.QEvent event;

    public final dev.bum.ticket_service.jpa.reservation.QReservation reservation;

    public final dev.bum.ticket_service.jpa.seat.QSeat seat;

    public final EnumPath<dev.bum.ticket_service.enums.TicketStatus> status = createEnum("status", dev.bum.ticket_service.enums.TicketStatus.class);

    public final NumberPath<Long> ticketId = createNumber("ticketId", Long.class);

    public final StringPath userId = createString("userId");

    public QTicket(String variable) {
        this(Ticket.class, forVariable(variable), INITS);
    }

    public QTicket(Path<? extends Ticket> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QTicket(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QTicket(PathMetadata metadata, PathInits inits) {
        this(Ticket.class, metadata, inits);
    }

    public QTicket(Class<? extends Ticket> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.event = inits.isInitialized("event") ? new dev.bum.ticket_service.jpa.event.QEvent(forProperty("event")) : null;
        this.reservation = inits.isInitialized("reservation") ? new dev.bum.ticket_service.jpa.reservation.QReservation(forProperty("reservation"), inits.get("reservation")) : null;
        this.seat = inits.isInitialized("seat") ? new dev.bum.ticket_service.jpa.seat.QSeat(forProperty("seat"), inits.get("seat")) : null;
    }

}

