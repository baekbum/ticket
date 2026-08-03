package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EventRepository {
    Event insert(InsertEventRequest info);
    void isExist(EventCondRequest cond);
    Event selectById(Long id);
    List<Event> selectByEventGroupCode(String eventGroupCode);
    Page<Event> selectByCond(EventCondRequest cond, Pageable pageable);
    Event update(Long id, UpdateEventRequest info);
    Event delete(Long id);
}
