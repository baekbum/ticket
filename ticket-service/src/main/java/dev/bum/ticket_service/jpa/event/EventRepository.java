package dev.bum.ticket_service.jpa.event;

import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepository {
    Event insert(InsertEventInfo info);
    void isExist(EventCond cond);
    Event selectById(Long id);
    Page<Event> selectByCond(EventCond cond, Pageable pageable);
    Event update(Long id, UpdateEventInfo info);
    Event delete(Long id);
}
