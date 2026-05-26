package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.exception.SeatNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.EventRepositoryImpl;
import dev.bum.ticket_service.vo.seat.*;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
@Import({
        SeatRepositoryImpl.class,
        EventRepositoryImpl.class,
        QuerydslConfig.class
})
@ActiveProfiles("test")
@DataJpaTest()
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // H2 같은 내장 DB 사용 강제
class SeatRepositoryImplTest {

    @Autowired
    private SeatRepositoryImpl seatRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private EntityManager entityManager;
    
    private long eventId;
    private Seat firstSeat;
    private Seat secondSeat;
    private Seat thirdSeat;

    @BeforeEach
    void info_set_up() throws Exception {
        // 이벤트 정보 등록
        Event event = Event.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(14500)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();

        Event savedEvent = eventJpaRepository.save(event);
        this.eventId = savedEvent.getEventId();

        // 좌석 정보 등록
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor A구역")
                .rows(10)
                .cols(10)
                .price(168000)
                .build();

        InsertSeatAreaConfig r_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.R)
                .zone("1구역")
                .rows(10)
                .cols(10)
                .price(145000)
                .build();

        InsertSeatAreaConfig s_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.S)
                .zone("10구역")
                .rows(10)
                .cols(10)
                .price(128000)
                .build();

        InsertSeatAreaConfig a_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.A)
                .zone("20구역")
                .rows(10)
                .cols(10)
                .price(128000)
                .build();

        List<InsertSeatAreaConfig> insertSeatAreaConfigList = List.of(vip_seat, r_seat, s_seat, a_seat);

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .insertSeatAreaConfigs(insertSeatAreaConfigList)
                .build();

        seatRepository.insert(info);

        List<Seat> savedSeats = seatJpaRepository.findAll();
        this.firstSeat = savedSeats.get(0);
        this.secondSeat = savedSeats.get(1);
        this.thirdSeat = savedSeats.get(2);
    }

    @Test
    @DisplayName("좌석 정보 추가")
    void seat_insert() throws Exception {
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor B구역")
                .rows(10)
                .cols(10)
                .price(168000)
                .build();

        List<InsertSeatAreaConfig> insertSeatAreaConfigList = List.of(vip_seat);

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .insertSeatAreaConfigs(insertSeatAreaConfigList)
                .build();

        seatRepository.insert(info);

        long totalCnt = seatRepository.countByEventId(this.eventId);

        assertThat(totalCnt).isEqualTo(500); // 각 등급마다 100자리씩
    }

    @Test
    @DisplayName("ID로 좌석 정보 조회")
    void seat_select_by_id() throws Exception {
        long seatId = firstSeat.getSeatId();
        Seat response = seatRepository.selectById(seatId);

        assertThat(response.getSeatId()).isEqualTo(seatId);
        assertThat(response.getEvent().getEventId()).isEqualTo(this.eventId);
        assertThat(response.getZone()).isEqualTo(firstSeat.getZone());
        assertThat(response.getSeatRow()).isEqualTo(firstSeat.getSeatRow());
        assertThat(response.getSeatCol()).isEqualTo(firstSeat.getSeatCol());
        assertThat(response.getGrade()).isEqualTo(firstSeat.getGrade());
        assertThat(response.getPrice()).isEqualTo(firstSeat.getPrice());
    }

    @Test
    @DisplayName("조건을 통해 좌석 정보 조회")
    void seat_select_by_cond() throws Exception {
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor B구역")
                .rows(10)
                .cols(10)
                .price(168000)
                .build();

        List<InsertSeatAreaConfig> insertSeatAreaConfigList = List.of(vip_seat);

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .insertSeatAreaConfigs(insertSeatAreaConfigList)
                .build();

        seatRepository.insert(info);

        SeatCond cond = SeatCond.builder()
                .grade(SeatGrade.VIP)
                .build();

        Pageable pageable = PageRequest.of(cond.getPage(), 300); // 테스트를 위해 사이즈를 300으로 늘림

        Page<Seat> seats = seatRepository.selectByCond(cond, pageable);

        assertThat(seats.getContent().size()).isEqualTo(200);
    }

    @Test
    @DisplayName("좌석 정보 수정")
    void seat_update() throws Exception {

        UpdateSeatAreaConfig config_1 = UpdateSeatAreaConfig.builder()
                .id(firstSeat.getSeatId())
                .status(SeatStatus.LOCKED)
                .build();

        UpdateSeatAreaConfig config_2 = UpdateSeatAreaConfig.builder()
                .id(secondSeat.getSeatId())
                .status(SeatStatus.LOCKED)
                .build();

        List<UpdateSeatAreaConfig> updateSeatAreaConfigList = List.of(config_1, config_2);

        UpdateSeatInfo info = UpdateSeatInfo.builder()
                .updateSeatAreaConfigs(updateSeatAreaConfigList)
                .build();

        seatRepository.update(info);

        Seat seat_1 = seatRepository.selectById(firstSeat.getSeatId());
        Seat seat_2 = seatRepository.selectById(secondSeat.getSeatId());
        Seat seat_3 = seatRepository.selectById(thirdSeat.getSeatId());

        assertThat(seat_1.getSeatId()).isEqualTo(firstSeat.getSeatId());
        assertThat(seat_1.getStatus()).isEqualTo(SeatStatus.LOCKED);

        assertThat(seat_2.getSeatId()).isEqualTo(secondSeat.getSeatId());
        assertThat(seat_2.getStatus()).isEqualTo(SeatStatus.LOCKED);

        assertThat(seat_3.getSeatId()).isEqualTo(thirdSeat.getSeatId());
        assertThat(seat_3.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("좌석 정보 삭제")
    void seat_delete() throws Exception {
        long seatId = firstSeat.getSeatId();

        seatRepository.delete(seatId);

        assertThatThrownBy(() -> seatRepository.selectById(seatId))
                .isInstanceOf(SeatNotExistException.class)
                .hasMessageContaining("해당 좌석 정보는 존재하지 않습니다.");
    }
}