package dev.bum.ticket_service.jpa.area;

import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.exception.area.AreaNotExistException;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({AreaRepositoryImpl.class, EventRepositoryImpl.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AreaRepositoryImplTest {

    @Autowired
    private AreaRepositoryImpl areaRepository;

    @Autowired
    private AreaJpaRepository areaJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    private Event event;

    @BeforeEach
    void setUp() {
        event = eventJpaRepository.save(event());
        areaJpaRepository.save(area("VIP", SeatGrade.VIP, 150000));
        areaJpaRepository.save(area("R", SeatGrade.R, 120000));
    }

    @Test
    @DisplayName("구역 등록")
    void area_insert() {
        InsertAreaRequest info = insertRequest("S", SeatGrade.S, 90000);

        Area response = areaRepository.insert(info);

        assertThat(response.getAreaId()).isNotNull();
        assertThat(response.getEvent().getEventId()).isEqualTo(event.getEventId());
        assertThat(response.getAreaName()).isEqualTo("S");
        assertThat(response.getLayoutKey()).isEqualTo("S");
        assertThat(response.getGrade()).isEqualTo(SeatGrade.S);
        assertThat(response.getStatus()).isEqualTo(AreaStatus.ACTIVE);
    }

    @Test
    @DisplayName("동일 이벤트에 같은 구역명이 있으면 예외 발생")
    void area_insert_duplicate() {
        InsertAreaRequest info = insertRequest("VIP", SeatGrade.VIP, 150000);

        assertThatThrownBy(() -> areaRepository.insert(info))
                .isInstanceOf(AreaDuplicateException.class);
    }

    @Test
    @DisplayName("동일 이벤트에 같은 레이아웃 키가 있으면 예외 발생")
    void area_insert_duplicate_layout_key() {
        InsertAreaRequest info = InsertAreaRequest.builder()
                .eventId(event.getEventId())
                .areaName("VIP-A")
                .layoutKey("VIP")
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> areaRepository.insert(info))
                .isInstanceOf(AreaDuplicateException.class);
    }

    @Test
    @DisplayName("중복 구역이 없으면 중복 체크 통과")
    void is_exist_success() {
        AreaCondRequest cond = AreaCondRequest.builder()
                .eventId(event.getEventId())
                .areaName("S")
                .build();

        areaRepository.isExist(cond);
    }

    @Test
    @DisplayName("ID로 구역 조회")
    void area_select_by_id() {
        Area saved = areaJpaRepository.save(area("S", SeatGrade.S, 90000));

        Area response = areaRepository.selectById(saved.getAreaId());

        assertThat(response.getAreaId()).isEqualTo(saved.getAreaId());
        assertThat(response.getAreaName()).isEqualTo("S");
        assertThat(response.getLayoutKey()).isEqualTo("S");
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 예외 발생")
    void area_select_by_id_fail() {
        assertThatThrownBy(() -> areaRepository.selectById(999L))
                .isInstanceOf(AreaNotExistException.class);
    }

    @Test
    @DisplayName("조건으로 구역 조회")
    void area_select_by_cond() {
        AreaCondRequest cond = AreaCondRequest.builder()
                .eventId(event.getEventId())
                .grade(SeatGrade.VIP)
                .status(AreaStatus.ACTIVE)
                .build();

        Page<Area> response = areaRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getAreaName()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("구역명 like 조건으로 조회")
    void area_select_by_name_like() {
        AreaCondRequest cond = AreaCondRequest.builder()
                .areaName("V")
                .build();

        Page<Area> response = areaRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getAreaName()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("레이아웃 키 조건으로 조회")
    void area_select_by_layout_key() {
        AreaCondRequest cond = AreaCondRequest.builder()
                .layoutKey("VIP")
                .build();

        Page<Area> response = areaRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getAreaName()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("정렬 조건으로 구역 조회")
    void area_select_by_cond_with_sort() {
        AreaCondRequest cond = AreaCondRequest.builder().build();

        Page<Area> response = areaRepository.selectByCond(
                cond,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "price"))
        );

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getAreaName()).isEqualTo("VIP");
        assertThat(response.getContent().get(1).getAreaName()).isEqualTo("R");
    }

    @Test
    @DisplayName("구역 정보 수정")
    void area_update() {
        Area saved = areaJpaRepository.save(area("S", SeatGrade.S, 90000));
        UpdateAreaRequest info = UpdateAreaRequest.builder()
                .areaName("S-A")
                .layoutKey("section-s-a")
                .grade(SeatGrade.A)
                .price(70000)
                .status(AreaStatus.INACTIVE)
                .build();

        Area response = areaRepository.update(saved.getAreaId(), info);

        assertThat(response.getAreaName()).isEqualTo("S-A");
        assertThat(response.getLayoutKey()).isEqualTo("section-s-a");
        assertThat(response.getGrade()).isEqualTo(SeatGrade.A);
        assertThat(response.getPrice()).isEqualTo(70000);
        assertThat(response.getStatus()).isEqualTo(AreaStatus.INACTIVE);
    }

    @Test
    @DisplayName("구역 삭제")
    void area_delete() {
        Area saved = areaJpaRepository.save(area("S", SeatGrade.S, 90000));

        Area deleted = areaRepository.delete(saved.getAreaId());

        assertThat(deleted.getAreaId()).isEqualTo(saved.getAreaId());
        assertThatThrownBy(() -> areaRepository.selectById(saved.getAreaId()))
                .isInstanceOf(AreaNotExistException.class);
    }

    private InsertAreaRequest insertRequest(String areaName, SeatGrade grade, Integer price) {
        return InsertAreaRequest.builder()
                .eventId(event.getEventId())
                .areaName(areaName)
                .layoutKey(areaName)
                .grade(grade)
                .price(price)
                .status(AreaStatus.ACTIVE)
                .build();
    }

    private Area area(String areaName, SeatGrade grade, Integer price) {
        return Area.builder()
                .event(event)
                .areaName(areaName)
                .layoutKey(areaName)
                .grade(grade)
                .price(price)
                .status(AreaStatus.ACTIVE)
                .build();
    }

    private Event event() {
        return Event.builder()
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
                .totalSeats(1000)
                .availableSeats(1000)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }
}
