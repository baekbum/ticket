package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventJpaRepository extends JpaRepository<Event, Long> {

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
