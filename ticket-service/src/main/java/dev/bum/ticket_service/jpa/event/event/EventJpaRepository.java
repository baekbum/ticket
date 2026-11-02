package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventJpaRepository extends JpaRepository<Event, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Event e where e.eventId = :id")
    Optional<Event> findByIdForRead(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.eventId = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.eventId from Event e where e.eventId > :after order by e.eventId")
    List<Long> findIdsAfter(@Param("after") long after, Pageable pageable);

    List<Event> findByEventGroupCode(String eventGroupCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event e
            set e.status = :closedStatus
            where e.status in :targetStatuses
              and e.eventDateTime < :now
            """)
    int closeEventsAfterEventDateTime(
            @Param("targetStatuses") Collection<EventStatus> targetStatuses,
            @Param("closedStatus") EventStatus closedStatus,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event e
            set e.status = :saleEndedStatus
            where e.status = :onSaleStatus
              and e.saleEndAt < :now
            """)
    int endSalesAfterSaleEndAt(
            @Param("onSaleStatus") EventStatus onSaleStatus,
            @Param("saleEndedStatus") EventStatus saleEndedStatus,
            @Param("now") LocalDateTime now
    );
}
