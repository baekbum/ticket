package dev.bum.ticket_service.jpa.ticket;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QTicketPurchaseLock is a Querydsl query type for TicketPurchaseLock
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QTicketPurchaseLock extends EntityPathBase<TicketPurchaseLock> {

    private static final long serialVersionUID = 228991016L;

    public static final QTicketPurchaseLock ticketPurchaseLock = new QTicketPurchaseLock("ticketPurchaseLock");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<dev.bum.common.service.ticket.event.event.enums.TicketLimitScope> limitScope = createEnum("limitScope", dev.bum.common.service.ticket.event.event.enums.TicketLimitScope.class);

    public final StringPath scopeKey = createString("scopeKey");

    public final NumberPath<Long> ticketCount = createNumber("ticketCount", Long.class);

    public final StringPath userId = createString("userId");

    public QTicketPurchaseLock(String variable) {
        super(TicketPurchaseLock.class, forVariable(variable));
    }

    public QTicketPurchaseLock(Path<? extends TicketPurchaseLock> path) {
        super(path.getType(), path.getMetadata());
    }

    public QTicketPurchaseLock(PathMetadata metadata) {
        super(TicketPurchaseLock.class, metadata);
    }

}

