package dev.bum.user_service.jpa.user;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QUserKafkaEventState is a Querydsl query type for UserKafkaEventState
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUserKafkaEventState extends EntityPathBase<UserKafkaEventState> {

    private static final long serialVersionUID = 2102785666L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QUserKafkaEventState userKafkaEventState = new QUserKafkaEventState("userKafkaEventState");

    public final NumberPath<Long> eventVersion = createNumber("eventVersion", Long.class);

    public final NumberPath<Long> passwordResetVersion = createNumber("passwordResetVersion", Long.class);

    public final QUser user;

    public final NumberPath<Long> userPk = createNumber("userPk", Long.class);

    public QUserKafkaEventState(String variable) {
        this(UserKafkaEventState.class, forVariable(variable), INITS);
    }

    public QUserKafkaEventState(Path<? extends UserKafkaEventState> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QUserKafkaEventState(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QUserKafkaEventState(PathMetadata metadata, PathInits inits) {
        this(UserKafkaEventState.class, metadata, inits);
    }

    public QUserKafkaEventState(Class<? extends UserKafkaEventState> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.user = inits.isInitialized("user") ? new QUser(forProperty("user")) : null;
    }

}
