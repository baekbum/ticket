package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.exception.SeatNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.EventRepositoryImpl;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    
    private long eventId;

    @BeforeEach
    void eventInfoSetUp() throws Exception {
        Event event = Event.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(14500)
                .status(EventStatus.ON_SALE)
                .build();

        Event savedEvent = eventJpaRepository.save(event);

        this.eventId = savedEvent.getEventId();
    }

    @Test
    @DisplayName("좌석 정보 추가")
    void seat_insert() throws Exception {
        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("A-13")
                .grade(SeatGrade.VIP)
                .price(165000)
                .build();

        Seat response = seatRepository.insert(info);

        // 이벤트 관련
        assertThat(response.getEvent().getEventId()).isEqualTo(this.eventId);
        assertThat(response.getEvent().getArtistName()).isEqualTo("아이유");
        assertThat(response.getEvent().getTitle()).isEqualTo("아이유 콘서트");
        assertThat(response.getEvent().getVenue()).isEqualTo("올림픽 체조 경기장");
        assertThat(response.getEvent().getEventDate()).isEqualTo(LocalDateTime.of(2026, 9, 18, 18, 0));
        assertThat(response.getEvent().getTotalSeats()).isEqualTo(14500);
        assertThat(response.getEvent().getStatus()).isEqualTo(EventStatus.ON_SALE);

        // 좌석 관련
        assertThat(response.getSeatNumber()).isEqualTo("A-13");
        assertThat(response.getGrade()).isEqualTo(SeatGrade.VIP);
        assertThat(response.getPrice()).isEqualTo(165000);
    }

    @Test
    @DisplayName("ID로 좌석 정보 조회")
    void seat_select_by_id() throws Exception {
        InsertSeatInfo info_1 = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("A-13")
                .grade(SeatGrade.VIP)
                .price(165000)
                .build();

        InsertSeatInfo info_2 = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("28구역-7열-14번")
                .grade(SeatGrade.A)
                .price(108000)
                .build();

        Seat savedSeat_1 = seatRepository.insert(info_1);
        Seat savedSeat_2 = seatRepository.insert(info_2);

        Seat response_1 = seatRepository.selectById(savedSeat_1.getSeatId());
        Seat response_2 = seatRepository.selectById(savedSeat_2.getSeatId());

        assertThat(response_1.getSeatId()).isEqualTo(savedSeat_1.getSeatId());
        assertThat(response_1.getEvent().getEventId()).isEqualTo(this.eventId);
        assertThat(response_1.getSeatNumber()).isEqualTo("A-13");
        assertThat(response_1.getGrade()).isEqualTo(SeatGrade.VIP);
        assertThat(response_1.getPrice()).isEqualTo(165000);

        assertThat(response_2.getSeatId()).isEqualTo(savedSeat_2.getSeatId());
        assertThat(response_2.getEvent().getEventId()).isEqualTo(this.eventId);
        assertThat(response_2.getSeatNumber()).isEqualTo("28구역-7열-14번");
        assertThat(response_2.getGrade()).isEqualTo(SeatGrade.A);
        assertThat(response_2.getPrice()).isEqualTo(108000);
    }

    @Test
    @DisplayName("조건을 통해 좌석 정보 조회")
    void seat_select_by_cond() throws Exception {
        InsertSeatInfo info_1 = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("A-13")
                .grade(SeatGrade.VIP)
                .price(165000)
                .build();

        InsertSeatInfo info_2 = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("28구역-7열-14번")
                .grade(SeatGrade.A)
                .price(108000)
                .build();

        InsertSeatInfo info_3 = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("28구역-10열-28번")
                .grade(SeatGrade.A)
                .price(108000)
                .build();

        seatRepository.insert(info_1);
        seatRepository.insert(info_2);
        seatRepository.insert(info_3);

        SeatCond cond = SeatCond.builder()
                .grade(SeatGrade.A)
                .build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<Seat> seats = seatRepository.selectByCond(cond, pageable);

        assertThat(seats.getContent().size()).isEqualTo(2);
        assertThat(seats.getContent().get(0).getSeatNumber()).isEqualTo("28구역-7열-14번");
        assertThat(seats.getContent().get(1).getSeatNumber()).isEqualTo("28구역-10열-28번");
    }

    @Test
    @DisplayName("좌석 정보 수정")
    void seat_update() throws Exception {
        InsertSeatInfo insertSeatInfo = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("A-13")
                .grade(SeatGrade.VIP)
                .price(165000)
                .build();

        Seat savedSeat = seatRepository.insert(insertSeatInfo);

        assertThat(savedSeat.getSeatNumber()).isEqualTo("A-13");

        long seatId = savedSeat.getSeatId();

        UpdateSeatInfo updateSeatInfo = UpdateSeatInfo.builder()
                .seatNumber("C-11")
                .build();

        Seat updatedSeat = seatRepository.update(seatId, updateSeatInfo);

        assertThat(updatedSeat.getSeatId()).isEqualTo(seatId);
        assertThat(updatedSeat.getSeatNumber()).isEqualTo("C-11");
    }

    @Test
    @DisplayName("좌석 정보 삭제")
    void seat_delete() throws Exception {
        InsertSeatInfo insertSeatInfo = InsertSeatInfo.builder()
                .eventId(this.eventId)
                .seatNumber("A-13")
                .grade(SeatGrade.VIP)
                .price(165000)
                .build();

        Seat savedSeat = seatRepository.insert(insertSeatInfo);

        long seatId = savedSeat.getSeatId();

        seatRepository.delete(seatId);

        assertThatThrownBy(() -> seatRepository.selectById(seatId))
                .isInstanceOf(SeatNotExistException.class)
                .hasMessageContaining("해당 좌석 정보는 존재하지 않습니다.");
    }
}