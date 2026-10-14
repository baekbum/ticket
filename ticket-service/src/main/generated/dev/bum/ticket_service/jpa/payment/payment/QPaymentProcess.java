package dev.bum.ticket_service.jpa.payment.payment;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPaymentProcess is a Querydsl query type for PaymentProcess
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentProcess extends EntityPathBase<PaymentProcess> {

    private static final long serialVersionUID = -1194533557L;

    public static final QPaymentProcess paymentProcess = new QPaymentProcess("paymentProcess");

    public final StringPath failureReason = createString("failureReason");

    public final DateTimePath<java.time.LocalDateTime> lastTriedAt = createDateTime("lastTriedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> paymentProcessId = createNumber("paymentProcessId", Long.class);

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<PaymentProcessStage> stage = createEnum("stage", PaymentProcessStage.class);

    public QPaymentProcess(String variable) {
        super(PaymentProcess.class, forVariable(variable));
    }

    public QPaymentProcess(Path<? extends PaymentProcess> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPaymentProcess(PathMetadata metadata) {
        super(PaymentProcess.class, metadata);
    }

}
