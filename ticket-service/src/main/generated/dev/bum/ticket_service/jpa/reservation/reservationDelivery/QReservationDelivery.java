package dev.bum.ticket_service.jpa.reservation.reservationDelivery;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReservationDelivery is a Querydsl query type for ReservationDelivery
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReservationDelivery extends EntityPathBase<ReservationDelivery> {

    private static final long serialVersionUID = 1716035070L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QReservationDelivery reservationDelivery = new QReservationDelivery("reservationDelivery");

    public final StringPath address = createString("address");

    public final StringPath carrier = createString("carrier");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> deliveredAt = createDateTime("deliveredAt", java.time.LocalDateTime.class);

    public final StringPath deliveryMessage = createString("deliveryMessage");

    public final StringPath detailAddress = createString("detailAddress");

    public final StringPath recipientName = createString("recipientName");

    public final StringPath recipientPhone = createString("recipientPhone");

    public final dev.bum.ticket_service.jpa.reservation.reservation.QReservation reservation;

    public final NumberPath<Long> reservationDeliveryId = createNumber("reservationDeliveryId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> shippedAt = createDateTime("shippedAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus> status = createEnum("status", dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus.class);

    public final StringPath trackingNumber = createString("trackingNumber");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath zipCode = createString("zipCode");

    public QReservationDelivery(String variable) {
        this(ReservationDelivery.class, forVariable(variable), INITS);
    }

    public QReservationDelivery(Path<? extends ReservationDelivery> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QReservationDelivery(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QReservationDelivery(PathMetadata metadata, PathInits inits) {
        this(ReservationDelivery.class, metadata, inits);
    }

    public QReservationDelivery(Class<? extends ReservationDelivery> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.reservation = inits.isInitialized("reservation") ? new dev.bum.ticket_service.jpa.reservation.reservation.QReservation(forProperty("reservation"), inits.get("reservation")) : null;
    }

}

