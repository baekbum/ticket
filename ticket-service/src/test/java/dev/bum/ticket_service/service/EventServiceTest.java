package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.service.event.event.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;

    @Mock
    private EventRepository repository;

    @Test
    @DisplayName("사용자용 이벤트 상세 조회")
    void select_visible_by_id() {
        Event event = event(1L, "IU Concert");

        given(repository.selectById(1L)).willReturn(event);

        EventResponse response = eventService.selectVisibleById(1L);

        assertThat(response.getEventId()).isEqualTo(1L);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("사용자용 이벤트 상세 조회 시 판매중이 아니면 예외 발생")
    void select_visible_by_id_fail_not_on_sale() {
        Event event = event(1L, "IU Concert", EventStatus.CANCELLED);

        given(repository.selectById(1L)).willReturn(event);

        assertThatThrownBy(() -> eventService.selectVisibleById(1L))
                .isInstanceOf(EventNotExistException.class);

        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("사용자용 이벤트 목록 조회 시 판매중 상태로 제한")
    void select_visible_by_cond() {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("IU")
                .build();
        Page<Event> page = new PageImpl<>(List.of(event(1L, "IU Concert")), PageRequest.of(0, 10), 1);

        given(repository.selectByCond(argThat(request ->
                "IU".equals(request.getArtistName()) && request.getStatus() == EventStatus.ON_SALE
        ), argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 10))).willReturn(page);

        CustomPageResponse<EventResponse> response = eventService.selectVisibleByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(cond.getStatus()).isEqualTo(EventStatus.ON_SALE);
        then(repository).should().selectByCond(argThat(request ->
                "IU".equals(request.getArtistName()) && request.getStatus() == EventStatus.ON_SALE
        ), argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
    }

    private Event event(Long eventId, String title) {
        return event(eventId, title, EventStatus.ON_SALE);
    }

    private Event event(Long eventId, String title, EventStatus status) {
        return Event.builder()
                .eventId(eventId)
                .artistName("IU")
                .title(title)
                .description("Concert description")
                .venue("KSPO Dome")
                .venueAddress("Seoul")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .saleStartAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .saleEndAt(LocalDateTime.of(2026, 9, 17, 23, 59))
                .cancelDeadlineAt(LocalDateTime.of(2026, 9, 17, 17, 0))
                .runningMinutes(120)
                .ageLimit(12)
                .totalSeats(14500)
                .availableSeats(14500)
                .status(status)
                .maxTicketsPerPerson(4)
                .build();
    }
}
