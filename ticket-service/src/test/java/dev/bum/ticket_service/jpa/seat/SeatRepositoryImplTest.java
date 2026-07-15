package dev.bum.ticket_service.jpa.seat;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.UpdateSeatRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.exception.seat.SeatDuplicateException;
import dev.bum.ticket_service.exception.seat.SeatNotExistException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.area.AreaRepositoryImpl;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({
        SeatRepositoryImpl.class,
        AreaRepositoryImpl.class,
        EventRepositoryImpl.class,
        QuerydslConfig.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SeatRepositoryImplTest {

    @Autowired
    private SeatRepositoryImpl seatRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private AreaJpaRepository areaJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Event event;
    private Area area;

    @BeforeEach
    void setUp() {
        event = eventJpaRepository.save(event());
        area = areaJpaRepository.save(area(event));

        seatRepository.insert(insertRequest("VIP", 2, 2));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("좌석 등록")
    void seat_insert() {
        seatRepository.insert(insertRequest("R", 1, 2));

        assertThat(seatRepository.countByEventId(event.getEventId())).isEqualTo(6);
    }

    @Test
    @DisplayName("예약 불가능 상태 좌석이 있으면 중복 예외 발생")
    void seat_insert_duplicate_when_not_available_seat_exists() {
        Seat firstSeat = seatJpaRepository.findAll().get(0);
        firstSeat.lock();

        assertThatThrownBy(() -> seatRepository.insert(insertRequest("VIP", 2, 2)))
                .isInstanceOf(SeatDuplicateException.class);
    }

    @Test
    @DisplayName("이벤트 기준 좌석 수 조회")
    void count_by_event_id() {
        long count = seatRepository.countByEventId(event.getEventId());

        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("ID로 좌석 조회")
    void seat_select_by_id() {
        Seat firstSeat = seatJpaRepository.findAll().get(0);

        Seat response = seatRepository.selectById(firstSeat.getSeatId());

        assertThat(response.getSeatId()).isEqualTo(firstSeat.getSeatId());
        assertThat(response.getEvent().getEventId()).isEqualTo(event.getEventId());
        assertThat(response.getZone()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 예외 발생")
    void seat_select_by_id_fail() {
        assertThatThrownBy(() -> seatRepository.selectById(999L))
                .isInstanceOf(SeatNotExistException.class);
    }

    @Test
    @DisplayName("이벤트 ID로 좌석 조회")
    void seat_select_by_event_id() {
        List<Seat> response = seatRepository.selectByEventId(event.getEventId());

        assertThat(response).hasSize(4);
    }

    @Test
    @DisplayName("구역 ID로 좌석 조회")
    void seat_select_by_area_id() {
        List<Seat> response = seatRepository.selectByAreaId(area.getAreaId());

        assertThat(response).hasSize(4);
    }

    @Test
    @DisplayName("좌석 목록 조회")
    void seat_select_by_seat_list() {
        List<Seat> savedSeats = seatJpaRepository.findAll();
        List<SeatInfo> seatInfos = savedSeats.stream()
                .limit(2)
                .map(seat -> SeatInfo.builder()
                        .id(seat.getSeatId())
                        .zone(seat.getZone())
                        .row(seat.getSeatRow())
                        .col(seat.getSeatCol())
                        .build())
                .toList();

        List<Seat> response = seatRepository.selectBySeatList(event.getEventId(), seatInfos);

        assertThat(response).hasSize(2);
    }

    @Test
    @DisplayName("이미 선택된 좌석이 포함되면 예외 발생")
    void seat_select_by_seat_list_fail_with_locked_seat() {
        List<Seat> savedSeats = seatJpaRepository.findAll();
        Seat firstSeat = savedSeats.get(0);
        firstSeat.lock();
        List<SeatInfo> seatInfos = List.of(SeatInfo.builder()
                .id(firstSeat.getSeatId())
                .zone(firstSeat.getZone())
                .row(firstSeat.getSeatRow())
                .col(firstSeat.getSeatCol())
                .build());

        assertThatThrownBy(() -> seatRepository.selectBySeatList(event.getEventId(), seatInfos))
                .isInstanceOf(SeatNotExistException.class);
    }

    @Test
    @DisplayName("조건으로 좌석 조회")
    void seat_select_by_cond() {
        SeatCondRequest cond = SeatCondRequest.builder()
                .eventId(event.getEventId())
                .areaId(area.getAreaId())
                .grade(SeatGrade.VIP)
                .status(SeatStatus.AVAILABLE)
                .build();

        Page<Seat> response = seatRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("좌석 수정")
    void seat_update() {
        Seat firstSeat = seatJpaRepository.findAll().get(0);
        UpdateSeatRequest info = UpdateSeatRequest.builder()
                .updateSeatAreaConfigs(List.of(UpdateSeatAreaConfig.builder()
                        .id(firstSeat.getSeatId())
                        .status(SeatStatus.LOCKED)
                        .price(160000)
                        .positionX(100D)
                        .build()))
                .build();

        seatRepository.update(info);

        Seat response = seatRepository.selectById(firstSeat.getSeatId());
        assertThat(response.getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(response.getPrice()).isEqualTo(160000);
        assertThat(response.getPositionX()).isEqualTo(100D);
    }

    @Test
    @DisplayName("좌석 삭제")
    void seat_delete() {
        Seat firstSeat = seatJpaRepository.findAll().get(0);

        seatRepository.delete(firstSeat.getSeatId());

        assertThatThrownBy(() -> seatRepository.selectById(firstSeat.getSeatId()))
                .isInstanceOf(SeatNotExistException.class);
    }

    @Test
    @DisplayName("좌석 ID 목록 삭제")
    void seat_delete_by_id_list() {
        List<Long> ids = seatJpaRepository.findAll().stream()
                .limit(2)
                .map(Seat::getSeatId)
                .toList();

        seatRepository.deleteByIdList(ids);
        entityManager.flush();
        entityManager.clear();

        assertThat(seatRepository.countByEventId(event.getEventId())).isEqualTo(2);
    }

    @Test
    @DisplayName("구역 ID 기준 좌석 삭제")
    void seat_delete_by_area_id() {
        seatRepository.deleteByAreaId(area.getAreaId());
        entityManager.flush();
        entityManager.clear();

        assertThat(seatJpaRepository.findByAreaAreaId(area.getAreaId())).isEmpty();
    }

    private InsertSeatRequest insertRequest(String zone, int rows, int cols) {
        return InsertSeatRequest.builder()
                .eventId(event.getEventId())
                .areaId(area.getAreaId())
                .insertSeatAreaConfigs(List.of(InsertSeatAreaConfig.builder()
                        .grade(SeatGrade.VIP)
                        .zone(zone)
                        .rows(rows)
                        .cols(cols)
                        .price(150000)
                        .startX(10D)
                        .startY(20D)
                        .seatWidth(14D)
                        .seatHeight(14D)
                        .gapX(4D)
                        .gapY(4D)
                        .build()))
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

    private Area area(Event event) {
        return Area.builder()
                .event(event)
                .areaName("VIP")
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }
}
