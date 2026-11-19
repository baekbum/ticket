package dev.bum.ticket_service.jpa.payment.virtualAccount;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QGatewayBankCancellationReceipt is a Querydsl query type for GatewayBankCancellationReceipt
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QGatewayBankCancellationReceipt extends EntityPathBase<GatewayBankCancellationReceipt> {

    private static final long serialVersionUID = -1816201241L;

    public static final QGatewayBankCancellationReceipt gatewayBankCancellationReceipt = new QGatewayBankCancellationReceipt("gatewayBankCancellationReceipt");

    public final StringPath eventId = createString("eventId");

    public final MapPath<String, Object, SimplePath<Object>> payload = this.<String, Object, SimplePath<Object>>createMap("payload", String.class, Object.class, SimplePath.class);

    public final StringPath paymentNo = createString("paymentNo");

    public final DateTimePath<java.time.LocalDateTime> receivedAt = createDateTime("receivedAt", java.time.LocalDateTime.class);

    public QGatewayBankCancellationReceipt(String variable) {
        super(GatewayBankCancellationReceipt.class, forVariable(variable));
    }

    public QGatewayBankCancellationReceipt(Path<? extends GatewayBankCancellationReceipt> path) {
        super(path.getType(), path.getMetadata());
    }

    public QGatewayBankCancellationReceipt(PathMetadata metadata) {
        super(GatewayBankCancellationReceipt.class, metadata);
    }

}
