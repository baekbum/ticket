package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.service.event.event.EventService;
import dev.bum.ticket_service.service.event.file.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;

    @Mock
    private EventRepository repository;

    @Mock
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("이벤트 등록 시 포스터 저장 후 URL 반영")
    void insert() {
        InsertEventRequest info = insertRequest();
        MultipartFile posterImage = posterImage();
        Event event = event(1L, "IU Concert");

        given(repository.insert(info)).willReturn(event);
        given(fileStorageService.saveEventPoster(1L, posterImage)).willReturn("/ticket/uploads/events/posters/1/poster.png");

        EventResponse response = eventService.insert(info, posterImage);

        assertThat(response.getEventId()).isEqualTo(1L);
        assertThat(response.getPosterUrl()).isEqualTo("/ticket/uploads/events/posters/1/poster.png");
        then(repository).should().insert(info);
        then(fileStorageService).should().saveEventPoster(1L, posterImage);
    }

    @Test
    @DisplayName("ID로 이벤트 조회")
    void select_by_id() {
        Event event = event(1L, "IU Concert");

        given(repository.selectById(1L)).willReturn(event);

        EventResponse response = eventService.selectById(1L);

        assertThat(response.getEventId()).isEqualTo(1L);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 이벤트 조회")
    void select_by_cond() {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("IU")
                .sort(List.of("eventId-desc"))
                .build();
        Page<Event> page = new PageImpl<>(List.of(event(1L, "IU Concert")), PageRequest.of(0, 10), 1);

        given(repository.selectByCond(argThat(request -> "IU".equals(request.getArtistName())), argThat(pageable ->
                pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("eventId") != null
                        && pageable.getSort().getOrderFor("eventId").isDescending()
        ))).willReturn(page);

        CustomPageResponse<EventResponse> response = eventService.selectByCond(cond);

        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getEventId()).isEqualTo(1L);
        then(repository).should().selectByCond(argThat(request -> "IU".equals(request.getArtistName())), argThat(pageable ->
                pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("eventId") != null
                        && pageable.getSort().getOrderFor("eventId").isDescending()
        ));
    }

    @Test
    @DisplayName("JSON 이벤트 수정")
    void update() {
        UpdateEventRequest info = updateRequest();
        Event event = event(1L, "Updated Concert");

        given(repository.update(1L, info)).willReturn(event);

        EventResponse response = eventService.update(1L, info);

        assertThat(response.getTitle()).isEqualTo("Updated Concert");
        then(repository).should().update(1L, info);
    }

    @Test
    @DisplayName("포스터 포함 이벤트 수정 시 새 포스터 URL 반영 후 이전 포스터 삭제")
    void update_with_poster() {
        UpdateEventRequest info = updateRequest();
        MultipartFile posterImage = posterImage();
        Event event = event(1L, "IU Concert");
        event.updatePosterUrl("/ticket/uploads/events/posters/1/old.png");

        given(repository.selectById(1L)).willReturn(event);
        given(fileStorageService.saveEventPoster(1L, posterImage)).willReturn("/ticket/uploads/events/posters/1/new.png");

        EventResponse response = eventService.update(1L, info, posterImage);

        assertThat(response.getPosterUrl()).isEqualTo("/ticket/uploads/events/posters/1/new.png");
        assertThat(response.getTitle()).isEqualTo("Updated Concert");
        then(repository).should().selectById(1L);
        then(fileStorageService).should().saveEventPoster(1L, posterImage);
        then(fileStorageService).should().deleteByPublicUrl("/ticket/uploads/events/posters/1/old.png");
    }

    @Test
    @DisplayName("포스터 없이 이벤트 수정 시 이전 포스터를 삭제하지 않음")
    void update_without_new_poster() {
        UpdateEventRequest info = updateRequest();
        Event event = event(1L, "IU Concert");
        event.updatePosterUrl("/ticket/uploads/events/posters/1/old.png");

        given(repository.selectById(1L)).willReturn(event);
        given(fileStorageService.saveEventPoster(1L, null)).willReturn(null);

        EventResponse response = eventService.update(1L, info, null);

        assertThat(response.getPosterUrl()).isEqualTo("/ticket/uploads/events/posters/1/old.png");
        assertThat(response.getTitle()).isEqualTo("Updated Concert");
        then(fileStorageService).should().saveEventPoster(1L, null);
        then(fileStorageService).should(never()).deleteByPublicUrl(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이벤트 삭제")
    void delete() {
        Event event = event(1L, "IU Concert");

        given(repository.delete(1L)).willReturn(event);

        EventResponse response = eventService.delete(1L);

        assertThat(response.getEventId()).isEqualTo(1L);
        then(repository).should().delete(1L);
    }

    @Test
    @DisplayName("벌크 삭제 시 모든 이벤트 삭제")
    void delete_bulk() {
        DeleteEventBulkRequest info = DeleteEventBulkRequest.builder()
                .eventIds(List.of(1L, 2L))
                .build();
        Event event1 = event(1L, "Concert 1");
        Event event2 = event(2L, "Concert 2");

        given(repository.delete(1L)).willReturn(event1);
        given(repository.delete(2L)).willReturn(event2);

        eventService.deleteBulk(info);

        then(repository).should().delete(1L);
        then(repository).should().delete(2L);
    }

    @Test
    @DisplayName("벌크 삭제 대상이 비어있으면 예외 발생")
    void delete_bulk_empty_ids() {
        DeleteEventBulkRequest info = DeleteEventBulkRequest.builder()
                .eventIds(List.of())
                .build();

        assertThatThrownBy(() -> eventService.deleteBulk(info))
                .isInstanceOf(IllegalArgumentException.class);

        then(repository).should(never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private InsertEventRequest insertRequest() {
        return InsertEventRequest.builder()
                .artistName("IU")
                .title("IU Concert")
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
                .maxTicketsPerPerson(4)
                .build();
    }

    private UpdateEventRequest updateRequest() {
        return UpdateEventRequest.builder()
                .title("Updated Concert")
                .description("Updated description")
                .venue("Olympic Stadium")
                .venueAddress("Seoul")
                .eventDateTime(LocalDateTime.of(2026, 9, 19, 18, 0))
                .runningMinutes(130)
                .ageLimit(12)
                .totalSeats(15000)
                .availableSeats(15000)
                .maxTicketsPerPerson(2)
                .build();
    }

    private Event event(Long eventId, String title) {
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
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private MultipartFile posterImage() {
        return new MockMultipartFile("posterImage", "poster.png", "image/png", "image".getBytes());
    }
}
