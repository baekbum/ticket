package dev.bum.ticket_service.jpa.reservation;

import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.enums.ReservationStatus;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepositoryImpl;
import dev.bum.ticket_service.jpa.ticket.TicketRepositoryImpl;
import dev.bum.ticket_service.vo.reservation.CancelReservationInfo;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import dev.bum.ticket_service.vo.reservation.ReservationCond;
import dev.bum.ticket_service.vo.seat.InsertSeatAreaConfig;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatInfo;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({
        EventRepositoryImpl.class,
        SeatRepositoryImpl.class,
        ReservationRepositoryImpl.class,
        TicketRepositoryImpl.class,
        QuerydslConfig.class
})
@ActiveProfiles("test")
@DataJpaTest()
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // H2 같은 내장 DB 사용 강제
class ReservationRepositoryImplTest {

    @Autowired
    private EventRepositoryImpl eventRepository;

    @Autowired
    private SeatRepositoryImpl seatRepository;

    @Autowired
    private TicketRepositoryImpl ticketRepository;

    @Autowired
    private ReservationRepositoryImpl reservationRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Event event;
    private List<Seat> seatList;

    @BeforeEach
    void info_set_up() throws Exception {
        // 1. 이벤트 정보 등록
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

        this.event = eventJpaRepository.save(event);

        // 2. 좌석 정보 등록
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor-A")
                .rows(2)
                .cols(5)
                .price(168000)
                .build();

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(this.event.getEventId())
                .insertSeatAreaConfigs(List.of(vip_seat))
                .build();

        seatRepository.insert(info);

        this.seatList = seatJpaRepository.findAll();
    }

    @Test
    @DisplayName("1인 4매 제한 공연에 3좌석을 선택해서 예매하는 경우")
    void reservation_insert_1() throws Exception {
        String userId = "IU";

        List<Seat> seats = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2)
        );

        InsertReservationInfo info = getInsertReservationInfo(seats, userId, this.event);

        // 1. 예매 내역 생성
        reservationRepository.insert(info);
    }

    @Test
    @DisplayName("1인 4매 제한 공연에 2좌석이 예매된 상태에서 2좌석을 추가로 예매하는 경우")
    void reservation_insert_2() throws Exception {
        String userId = "IU";

        // 처음 2매 예매
        List<Seat> seats_1 = List.of(
                this.seatList.get(0),
                this.seatList.get(1)
        );

        InsertReservationInfo info_1 = getInsertReservationInfo(seats_1, userId, this.event);

        reservationRepository.insert(info_1);

        // 추가적으로 2매 예매
        List<Seat> seats_2 = List.of(
                this.seatList.get(2),
                this.seatList.get(3)
        );

        InsertReservationInfo info_2 = getInsertReservationInfo(seats_2, userId, this.event);

        reservationRepository.insert(info_2);
    }

    @Test
    @DisplayName("매수 제한은 4매인데, 좌석을 5석 선택한 경우")
    void reservation_insert_fail_over_limit_1() throws Exception {
        String userId = "IU";
        int maxLimit = this.event.getMaxTicketsPerPerson();

        List<Seat> seats = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2),
                this.seatList.get(3),
                this.seatList.get(4)
        );

        InsertReservationInfo info = getInsertReservationInfo(seats, userId, this.event);

        assertThatThrownBy(() -> reservationRepository.insert(info))
                .isInstanceOf(TicketLimitExceededException.class)
                .hasMessageContaining(String.format("1인당 최대 예매 가능 수량은 %d매입니다.", maxLimit));
    }

    @Test
    @DisplayName("매수 제한은 4매인데, 이미 2좌석을 선택했고 3좌석 이상을 추가적으로 티켓팅 하는 경우")
    void reservation_insert_fail_over_limit_2() throws Exception {
        String userId = "IU";
        int maxLimit = this.event.getMaxTicketsPerPerson();

        List<Seat> seats_1 = List.of(
                this.seatList.get(0),
                this.seatList.get(1)
        );

        InsertReservationInfo info_1 = getInsertReservationInfo(seats_1, userId, this.event);

        reservationRepository.insert(info_1);

        List<Seat> seats_2 = List.of(
                this.seatList.get(2),
                this.seatList.get(3),
                this.seatList.get(4)
        );

        InsertReservationInfo info_2 = getInsertReservationInfo(seats_2, userId, this.event);

        assertThatThrownBy(() -> reservationRepository.insert(info_2))
                .isInstanceOf(TicketLimitExceededException.class)
                .hasMessageContaining(String.format("이미 기존 예매 내역이 존재하여, 추가로 %d매를 초과하여 예매할 수 없습니다.", maxLimit));
    }

    @Test
    @DisplayName("ID로 예매 내역 조회")
    void reservation_select_by_id() throws Exception {
        String userId = "IU";

        List<Seat> seats = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2)
        );

        InsertReservationInfo info = getInsertReservationInfo(seats, userId, this.event);

        // 1. 예매 내역 생성
        Reservation savedReservation = reservationRepository.insert(info);

        long id = savedReservation.getReservationId();

        // 2. id를 통해 예매 내역 조회
        Reservation response = reservationRepository.selectById(id);

        assertThat(response.getReservationId()).isEqualTo(savedReservation.getReservationId());
        assertThat(response.getUserId()).isEqualTo(savedReservation.getUserId());
        assertThat(response.getTickets().size()).isEqualTo(savedReservation.getTickets().size());
        assertThat(response.getEvent()).isEqualTo(savedReservation.getEvent());
        assertThat(response.getReservedAt()).isEqualTo(savedReservation.getReservedAt());
        assertThat(response.getStatus()).isEqualTo(savedReservation.getStatus());
        assertThat(response.getCreatedAt()).isEqualTo(savedReservation.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(savedReservation.getUpdatedAt());
    }

    @Test
    @DisplayName("조건으로 예매 내역 조회")
    void reservation_select_by_cond() throws Exception {
        String userId = "IU";

        List<Seat> seats_1 = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2)
        );

        InsertReservationInfo info_1 = getInsertReservationInfo(seats_1, userId, this.event);

        // 첫번째 예매 내역 생성
        Reservation saved_1 = reservationRepository.insert(info_1);

        // 다른 공연 등록
        Event anotherEvent = Event.builder()
                .artistName("윤하")
                .title("윤하 콘서트")
                .description("올림픽 핸드볼 경기장에서 하는 윤하 콘서트")
                .venue("올림픽 핸드볼 경기장")
                .eventDateTime(LocalDateTime.of(2026, 12, 25, 18, 0))
                .totalSeats(5003)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();

        Event savedEvent = eventJpaRepository.save(anotherEvent);

        // 다른 공연의 좌석 등록
        InsertSeatAreaConfig a_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.A)
                .zone("30구역")
                .rows(2)
                .cols(5)
                .price(88000)
                .build();

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(savedEvent.getEventId())
                .insertSeatAreaConfigs(List.of(a_seat))
                .build();

        // 좌석 등록
        seatRepository.insert(info);

        // 좌석 조회
        List<Seat> seats = seatRepository.selectByEventId(savedEvent.getEventId());

        List<Seat> seats_2 = List.of(
                seats.get(0),
                seats.get(1),
                seats.get(2),
                seats.get(3)
        );

        InsertReservationInfo info_2 = getInsertReservationInfo(seats_2, userId, anotherEvent);

        // 두번째 예매 내역 생성
        Reservation saved_2 = reservationRepository.insert(info_2);

        // reservationId 내림차순 정렬
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("reservationId").descending());

        // 예매 내역 조회 및 검증
        ReservationCond cond_1 = ReservationCond.builder()
                .userId(userId)
                .build();

        Page<Reservation> reservations_1 = reservationRepository.selectByCond(cond_1, pageable);

        assertThat(reservations_1.getContent().size()).isEqualTo(2);
        assertThat(reservations_1.getContent().get(0).getEvent().getArtistName()).isEqualTo("윤하");
        assertThat(reservations_1.getContent().get(0).getTickets().size()).isEqualTo(4);
        assertThat(reservations_1.getContent().get(1).getEvent().getArtistName()).isEqualTo("아이유");
        assertThat(reservations_1.getContent().get(1).getTickets().size()).isEqualTo(3);

        ReservationCond cond_2 = ReservationCond.builder()
                .userId(userId)
                .event(anotherEvent)
                .build();

        Page<Reservation> reservations_2 = reservationRepository.selectByCond(cond_2, pageable);

        assertThat(reservations_2.getContent().size()).isEqualTo(1);
        assertThat(reservations_2.getContent().get(0).getEvent().getArtistName()).isEqualTo("윤하");
        assertThat(reservations_2.getContent().get(0).getTickets().size()).isEqualTo(4);
    }

    @Test
    @DisplayName("예매 내역에 해당하는 전체 티켓을 취소하는 케이스")
    void reservation_cancel_all() throws Exception {
        String userId = "IU";

        List<Seat> seats = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2)
        );

        InsertReservationInfo info = getInsertReservationInfo(seats, userId, this.event);
        // 1. 예매 내역 생성
        Reservation saved = reservationRepository.insert(info);

        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getTickets().get(0).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
        assertThat(saved.getTickets().get(1).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
        assertThat(saved.getTickets().get(2).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);

        // 2. 전체 티켓 취소.
        CancelReservationInfo cancelInfo = CancelReservationInfo.builder()
                .userId(userId)
                .selectedTicketIdList(new ArrayList<>())
                .eventId(this.event.getEventId())
                .build();

        reservationRepository.cancel(saved.getReservationId(), cancelInfo);

        Reservation cancelledReservation = reservationRepository.selectById(saved.getReservationId());

        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelledReservation.getTickets().get(0).getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelledReservation.getTickets().get(1).getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelledReservation.getTickets().get(2).getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("예매 내역에 해당하는 부분 티켓을 취소하는 케이스")
    void reservation_cancel_some() throws Exception {
        String userId = "IU";

        List<Seat> seats = List.of(
                this.seatList.get(0),
                this.seatList.get(1),
                this.seatList.get(2)
        );

        InsertReservationInfo info = getInsertReservationInfo(seats, userId, this.event);

        // 1. 예매 내역 생성
        Reservation saved = reservationRepository.insert(info);

        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getTickets().get(0).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
        assertThat(saved.getTickets().get(1).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
        assertThat(saved.getTickets().get(2).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);

        // 2. 전체 티켓 취소.
        List<Long> cancelTicketIdList = List.of(saved.getTickets().get(1).getTicketId());

        CancelReservationInfo cancelInfo = CancelReservationInfo.builder()
                .userId(userId)
                .selectedTicketIdList(cancelTicketIdList)
                .eventId(this.event.getEventId())
                .build();

        reservationRepository.cancel(saved.getReservationId(), cancelInfo);

        Reservation cancelledReservation = reservationRepository.selectById(saved.getReservationId());

        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(cancelledReservation.getTickets().get(0).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
        assertThat(cancelledReservation.getTickets().get(1).getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelledReservation.getTickets().get(2).getStatus()).isEqualTo(TicketStatus.READY_TO_PAY);
    }

    private InsertReservationInfo getInsertReservationInfo(List<Seat> seats, String userId, Event event) {
        List<SeatInfo> seatInfos = seats.stream()
                .map(seat -> new SeatInfo(
                        seat.getSeatId(),
                        seat.getZone(),
                        seat.getSeatRow(),
                        seat.getSeatCol()
                ))
                .toList();

        return InsertReservationInfo.builder()
                .userId(userId)
                .eventId(event.getEventId())
                .seats(seatInfos)
                .build();
    }
}