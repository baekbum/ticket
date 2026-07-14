package dev.bum.ticket_service.jpa.event;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@Transactional
@Import({EventRepositoryImpl.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // H2 같은 내장 DB 사용 강제
class EventRepositoryImplTest {

    @Autowired
    private EventRepositoryImpl eventRepository;

    @Autowired
    private EventJpaRepository jpaRepository;

    @BeforeEach
    void setUp() throws Exception {
        Event initData = Event.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(LocalDateTime.of(2026, 5, 16, 18, 0))
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .status(EventStatus.ON_SALE)
                .build();

        jpaRepository.save(initData);
    }

    @Test
    @DisplayName("이벤트 정보 추가")
    void event_insert() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 4, 29, 18, 0);

        InsertEventRequest info = InsertEventRequest.builder()
                .artistName("윤하")
                .title("윤하 소극장 콘서트")
                .description("2026 윤하 소극장 콘서트")
                .venue("신한카드홀")
                .eventDateTime(eventDateTime)
                .totalSeats(1768)
                .maxTicketsPerPerson(2)
                .build();

        Event response = eventRepository.insert(info);

        assertThat(response.getArtistName()).isEqualTo("윤하");
        assertThat(response.getTitle()).isEqualTo("윤하 소극장 콘서트");
        assertThat(response.getDescription()).isEqualTo("2026 윤하 소극장 콘서트");
        assertThat(response.getVenue()).isEqualTo("신한카드홀");
        assertThat(response.getEventDateTime()).isEqualTo(eventDateTime);
        assertThat(response.getTotalSeats()).isEqualTo(1768);
        assertThat(response.getMaxTicketsPerPerson()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(EventStatus.ON_SALE);
    }

    @Test
    @DisplayName("이벤트 정보 추가 시 이미 동일한 이벤트 정보가 존재하면 오류 반환")
    void event_already_exist() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 5, 16, 18, 0);

        InsertEventRequest info = InsertEventRequest.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime)
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        assertThatThrownBy(() -> eventRepository.insert(info))
                .isInstanceOf(EventDuplicateException.class)
                .hasMessageContaining("동일한 공연 정보가 이미 존재합니다.");
    }

    @Test
    @DisplayName("ID 값으로 이벤트 검색")
    void event_select_by_id() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 4, 29, 18, 0);

        InsertEventRequest info = InsertEventRequest.builder()
                .artistName("윤하")
                .title("윤하 소극장 콘서트")
                .description("2026 윤하 소극장 콘서트")
                .venue("신한카드홀")
                .eventDateTime(eventDateTime)
                .totalSeats(1768)
                .maxTicketsPerPerson(2)
                .build();

        eventRepository.insert(info);

        long eventId = 2L;

        Event response = eventRepository.selectById(eventId);

        assertThat(response.getEventId()).isEqualTo(2L);
        assertThat(response.getArtistName()).isEqualTo("윤하");
        assertThat(response.getTitle()).isEqualTo("윤하 소극장 콘서트");
        assertThat(response.getDescription()).isEqualTo("2026 윤하 소극장 콘서트");
        assertThat(response.getVenue()).isEqualTo("신한카드홀");
        assertThat(response.getEventDateTime()).isEqualTo(eventDateTime);
        assertThat(response.getTotalSeats()).isEqualTo(1768);
        assertThat(response.getMaxTicketsPerPerson()).isEqualTo(2);
    }

    @Test
    @DisplayName("조건으로 이벤트 검색")
    void event_select_by_cond() throws Exception {
        LocalDateTime eventDateTime_1 = LocalDateTime.of(2026, 4, 29, 18, 0);
        LocalDateTime eventDateTime_2 = LocalDateTime.of(2026, 9, 18, 18, 0);

        InsertEventRequest info_1 = InsertEventRequest.builder()
                .artistName("윤하")
                .title("윤하 소극장 콘서트")
                .description("2026 윤하 소극장 콘서트")
                .venue("신한카드홀")
                .eventDateTime(eventDateTime_1)
                .totalSeats(1768)
                .maxTicketsPerPerson(2)
                .build();

        InsertEventRequest info_2 = InsertEventRequest.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("상암 월드컵 경기장에서 하는 아이유 콘서트")
                .venue("상암 월드컵 경기장")
                .eventDateTime(eventDateTime_2)
                .totalSeats(60000)
                .maxTicketsPerPerson(4)
                .build();

        eventRepository.insert(info_1);
        eventRepository.insert(info_2);

        EventCondRequest cond = EventCondRequest.builder()
                .artistName("아이유")
                .build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<Event> response = eventRepository.selectByCond(cond, pageable);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent().get(0).getArtistName()).isEqualTo("아이유");
        assertThat(response.getContent().get(1).getArtistName()).isEqualTo("아이유");
        assertThat(response.getContent().get(0).getVenue()).isEqualTo("올림픽 체조 경기장");
        assertThat(response.getContent().get(1).getVenue()).isEqualTo("상암 월드컵 경기장");
    }

    @Test
    @DisplayName("이벤트 정보 수정")
    void event_update() throws Exception {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<Event> events = eventRepository.selectByCond(cond, pageable);

        Event event = events.getContent().get(0);

        assertThat(event.getTitle()).isEqualTo("아이유 콘서트");
        assertThat(event.getDescription()).isEqualTo("올림픽 체조 경기장에서 하는 아이유 콘서트");
        assertThat(event.getVenue()).isEqualTo("올림픽 체조 경기장");
        assertThat(event.getEventDateTime()).isEqualTo(LocalDateTime.of(2026, 5, 16, 18, 0));
        assertThat(event.getTotalSeats()).isEqualTo(14500);

        LocalDateTime eventDateTime = LocalDateTime.of(2026, 9, 18, 17, 0);

        UpdateEventRequest info = UpdateEventRequest.builder()
                .description("고양 종합 운동장에서 하는 아이유 콘서트")
                .venue("고양 종합 운동장")
                .eventDateTime(eventDateTime)
                .totalSeats(43000)
                .build();

        Event response = eventRepository.update(event.getEventId(), info);

        assertThat(response.getTitle()).isEqualTo("아이유 콘서트");
        assertThat(response.getDescription()).isEqualTo("고양 종합 운동장에서 하는 아이유 콘서트");
        assertThat(response.getVenue()).isEqualTo("고양 종합 운동장");
        assertThat(response.getEventDateTime()).isEqualTo(eventDateTime);
        assertThat(response.getTotalSeats()).isEqualTo(43000);
    }

    @Test
    @DisplayName("이벤트 정보 삭제")
    void event_delete() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 4, 29, 18, 0);

        InsertEventRequest info= InsertEventRequest.builder()
                .artistName("윤하")
                .title("윤하 소극장 콘서트")
                .description("2026 윤하 소극장 콘서트")
                .venue("신한카드홀")
                .eventDateTime(eventDateTime)
                .totalSeats(1768)
                .maxTicketsPerPerson(2)
                .build();

        Event event = eventRepository.insert(info);

        Long eventId = event.getEventId();

        Event response = eventRepository.selectById(eventId);

        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getArtistName()).isEqualTo("윤하");
        assertThat(response.getTitle()).isEqualTo("윤하 소극장 콘서트");
        assertThat(response.getDescription()).isEqualTo("2026 윤하 소극장 콘서트");
        assertThat(response.getVenue()).isEqualTo("신한카드홀");
        assertThat(response.getEventDateTime()).isEqualTo(eventDateTime);
        assertThat(response.getTotalSeats()).isEqualTo(1768);
        assertThat(response.getMaxTicketsPerPerson()).isEqualTo(2);

        eventRepository.delete(eventId);

        assertThatThrownBy(() -> eventRepository.selectById(eventId))
                .isInstanceOf(EventNotExistException.class)
                .hasMessageContaining("해당 이벤트 정보는 존재하지 않습니다.");
    }
}