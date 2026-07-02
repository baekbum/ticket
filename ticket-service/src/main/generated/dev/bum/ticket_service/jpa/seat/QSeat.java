package dev.bum.ticket_service.jpa.seat;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QSeat is a Querydsl query type for Seat
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSeat extends EntityPathBase<Seat> {

    private static final long serialVersionUID = 750187356L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QSeat seat = new QSeat("seat");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final dev.bum.ticket_service.jpa.event.QEvent event;

    public final EnumPath<dev.bum.common.service.ticket.seat.enums.SeatGrade> grade = createEnum("grade", dev.bum.common.service.ticket.seat.enums.SeatGrade.class);

    public final NumberPath<Double> layoutAngle = createNumber("layoutAngle", Double.class);

    public final NumberPath<Double> positionX = createNumber("positionX", Double.class);

    public final NumberPath<Double> positionY = createNumber("positionY", Double.class);

    public final NumberPath<Integer> price = createNumber("price", Integer.class);

    public final NumberPath<Double> rotation = createNumber("rotation", Double.class);

    public final NumberPath<Integer> seatCol = createNumber("seatCol", Integer.class);

    public final NumberPath<Double> seatHeight = createNumber("seatHeight", Double.class);

    public final NumberPath<Long> seatId = createNumber("seatId", Long.class);

    public final NumberPath<Integer> seatRow = createNumber("seatRow", Integer.class);

    public final NumberPath<Double> seatWidth = createNumber("seatWidth", Double.class);

    public final EnumPath<dev.bum.common.service.ticket.seat.enums.SeatStatus> status = createEnum("status", dev.bum.common.service.ticket.seat.enums.SeatStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath zone = createString("zone");

    public QSeat(String variable) {
        this(Seat.class, forVariable(variable), INITS);
    }

    public QSeat(Path<? extends Seat> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QSeat(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QSeat(PathMetadata metadata, PathInits inits) {
        this(Seat.class, metadata, inits);
    }

    public QSeat(Class<? extends Seat> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.event = inits.isInitialized("event") ? new dev.bum.ticket_service.jpa.event.QEvent(forProperty("event")) : null;
    }

}

