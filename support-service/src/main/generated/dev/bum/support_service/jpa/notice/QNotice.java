package dev.bum.support_service.jpa.notice;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QNotice is a Querydsl query type for Notice
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QNotice extends EntityPathBase<Notice> {

    private static final long serialVersionUID = -2040602363L;

    public static final QNotice notice = new QNotice("notice");

    public final StringPath authorId = createString("authorId");

    public final EnumPath<NoticeCategory> category = createEnum("category", NoticeCategory.class);

    public final StringPath content = createString("content");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath pinned = createBoolean("pinned");

    public final DateTimePath<java.time.LocalDateTime> publishedAt = createDateTime("publishedAt", java.time.LocalDateTime.class);

    public final EnumPath<dev.bum.support_service.jpa.common.PublicationStatus> status = createEnum("status", dev.bum.support_service.jpa.common.PublicationStatus.class);

    public final StringPath title = createString("title");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> version = createNumber("version", Long.class);

    public final NumberPath<Long> viewCount = createNumber("viewCount", Long.class);

    public QNotice(String variable) {
        super(Notice.class, forVariable(variable));
    }

    public QNotice(Path<? extends Notice> path) {
        super(path.getType(), path.getMetadata());
    }

    public QNotice(PathMetadata metadata) {
        super(Notice.class, metadata);
    }

}

