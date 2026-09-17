package dev.bum.support_service.jpa.notice;

import dev.bum.common.service.support.notice.enums.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeJpaRepository extends JpaRepository<Notice, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notice n
               set n.viewCount = n.viewCount + 1,
                   n.version = n.version + 1
             where n.id = :id
               and n.status = :status
            """)
    int increaseViewCount(
            @Param("id") Long id,
            @Param("status") PublicationStatus status
    );
}
