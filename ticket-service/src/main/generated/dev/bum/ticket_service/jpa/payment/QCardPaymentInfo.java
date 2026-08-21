package dev.bum.ticket_service.jpa.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QCardPaymentInfo is a Querydsl query type for CardPaymentInfo
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QCardPaymentInfo extends BeanPath<CardPaymentInfo> {

    private static final long serialVersionUID = 2104019210L;

    public static final QCardPaymentInfo cardPaymentInfo = new QCardPaymentInfo("cardPaymentInfo");

    public final EnumPath<dev.bum.common.service.ticket.payment.enums.CardCompany> cardCompany = createEnum("cardCompany", dev.bum.common.service.ticket.payment.enums.CardCompany.class);

    public final StringPath maskedCardNumber = createString("maskedCardNumber");

    public final StringPath transactionId = createString("transactionId");

    public QCardPaymentInfo(String variable) {
        super(CardPaymentInfo.class, forVariable(variable));
    }

    public QCardPaymentInfo(Path<? extends CardPaymentInfo> path) {
        super(path.getType(), path.getMetadata());
    }

    public QCardPaymentInfo(PathMetadata metadata) {
        super(CardPaymentInfo.class, metadata);
    }

}

