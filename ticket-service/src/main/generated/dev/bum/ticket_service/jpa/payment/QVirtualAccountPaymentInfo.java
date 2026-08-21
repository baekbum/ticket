package dev.bum.ticket_service.jpa.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVirtualAccountPaymentInfo is a Querydsl query type for VirtualAccountPaymentInfo
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QVirtualAccountPaymentInfo extends BeanPath<VirtualAccountPaymentInfo> {

    private static final long serialVersionUID = -1905320776L;

    public static final QVirtualAccountPaymentInfo virtualAccountPaymentInfo = new QVirtualAccountPaymentInfo("virtualAccountPaymentInfo");

    public final StringPath accountNumber = createString("accountNumber");

    public final StringPath bankName = createString("bankName");

    public final StringPath depositorName = createString("depositorName");

    public QVirtualAccountPaymentInfo(String variable) {
        super(VirtualAccountPaymentInfo.class, forVariable(variable));
    }

    public QVirtualAccountPaymentInfo(Path<? extends VirtualAccountPaymentInfo> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVirtualAccountPaymentInfo(PathMetadata metadata) {
        super(VirtualAccountPaymentInfo.class, metadata);
    }

}

