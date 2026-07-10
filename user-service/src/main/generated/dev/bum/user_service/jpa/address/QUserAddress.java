package dev.bum.user_service.jpa.address;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QUserAddress is a Querydsl query type for UserAddress
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUserAddress extends EntityPathBase<UserAddress> {

    private static final long serialVersionUID = 1388666876L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QUserAddress userAddress = new QUserAddress("userAddress");

    public final StringPath address = createString("address");

    public final NumberPath<Long> addressId = createNumber("addressId", Long.class);

    public final StringPath alias = createString("alias");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final BooleanPath defaultAddress = createBoolean("defaultAddress");

    public final StringPath detailAddress = createString("detailAddress");

    public final StringPath recipientName = createString("recipientName");

    public final StringPath recipientPhone = createString("recipientPhone");

    public final EnumPath<dev.bum.common.service.user.address.enums.AddressStatus> status = createEnum("status", dev.bum.common.service.user.address.enums.AddressStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final dev.bum.user_service.jpa.user.QUser user;

    public final StringPath zipCode = createString("zipCode");

    public QUserAddress(String variable) {
        this(UserAddress.class, forVariable(variable), INITS);
    }

    public QUserAddress(Path<? extends UserAddress> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QUserAddress(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QUserAddress(PathMetadata metadata, PathInits inits) {
        this(UserAddress.class, metadata, inits);
    }

    public QUserAddress(Class<? extends UserAddress> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.user = inits.isInitialized("user") ? new dev.bum.user_service.jpa.user.QUser(forProperty("user")) : null;
    }

}

