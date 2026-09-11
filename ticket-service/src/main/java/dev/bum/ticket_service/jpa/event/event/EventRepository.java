package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.dto.EventCardResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository {
    Event insert(InsertEventRequest info);
    void isExist(EventCondRequest cond);
    Event selectById(Long id);
    List<Event> selectByEventGroupCode(String eventGroupCode);
    List<EventCardResponse> selectSoonestOnSaleCards(LocalDateTime now, int limit);
    List<EventCardResponse> selectFestivalCards(LocalDateTime now, int limit);
    List<EventCardResponse> selectOpenSoonCards(LocalDateTime now, LocalDateTime deadline, int limit);
    List<EventCardResponse> selectWeeklyRecommendedCards(LocalDateTime now, LocalDateTime deadline, int limit);
    List<EventCardResponse> selectGenreOnSaleCards(EventGenre genre, LocalDateTime now, String sort, int page, int size);
    Page<Event> selectByCond(EventCondRequest cond, Pageable pageable);
    Event update(Long id, UpdateEventRequest info);
    Event delete(Long id);
}
