package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.enums.ReservationStatus;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.exception.TicketDuplicateException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepositoryImpl;
import dev.bum.ticket_service.vo.seat.InsertSeatAreaConfig;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
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
class TicketRepositoryImplTest {

    @Autowired
    private SeatRepositoryImpl seatRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    @Autowired
    private TicketRepositoryImpl ticketRepository;

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
                .eventDate(LocalDateTime.of(2026, 9, 18, 18, 0))
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
    @DisplayName("티켓 정보 추가")
    void ticket_insert() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);

        List<Seat> seats = List.of(first, second);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);
    }

    @Test
    @DisplayName("동일한 티켓 정보가 존재할 시 에러 반환")
    void ticket_insert_fail() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);

        List<Seat> seats = List.of(first, second);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);

        // 4. 또 다른 사용자가 같은 공연의 똑같은 좌석의 예매를 시도할 때.
        String otherUserId = "BUM";

        Reservation otherReservation = Reservation.builder()
                .userId(otherUserId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(otherReservation);

        List<Ticket> otherTickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(otherUserId)
                    .reservation(otherReservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            otherTickets.add(ticket);
        }

        assertThatThrownBy(() -> ticketRepository.insert(otherTickets))
                .isInstanceOf(TicketDuplicateException.class)
                .hasMessageContaining("이미 동일한 티켓 정보가 존재합니다.");
    }

    @Test
    @DisplayName("ID를 통해 티켓 조회")
    void ticket_select_by_id() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);

        List<Seat> seats = List.of(first, second);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);

        List<Ticket> response_1 = ticketRepository.selectByReservation(reservation);
        assertThat(response_1.size()).isEqualTo(2);

        Ticket ticket_1 = response_1.get(0);
        Ticket foundTicket_1 = ticketRepository.select(ticket_1.getTicketId());
        assertThat(ticket_1.getTicketId()).isEqualTo(foundTicket_1.getTicketId());
        assertThat(ticket_1.getUserId()).isEqualTo(foundTicket_1.getUserId());
        assertThat(ticket_1.getEvent()).isEqualTo(foundTicket_1.getEvent());
        assertThat(ticket_1.getSeat()).isEqualTo(foundTicket_1.getSeat());
        assertThat(ticket_1.getStatus()).isEqualTo(foundTicket_1.getStatus());

        // 동일한 공연에 추가적으로 티켓팅을 시도하는 경우.
        Reservation extraReservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(extraReservation);

        Seat third = this.seatList.get(2);

        List<Seat> extraSeats = List.of(third);

        List<Ticket> extraTickets = new ArrayList<>();

        for (Seat seat : extraSeats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(extraReservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            extraTickets.add(ticket);
        }

        ticketRepository.insert(extraTickets);

        List<Ticket> response_2 = ticketRepository.selectByReservation(extraReservation);
        assertThat(response_2.size()).isEqualTo(1);

        Ticket ticket_2 = response_2.get(0);
        Ticket foundTicket_2 = ticketRepository.select(ticket_2.getTicketId());
        assertThat(ticket_2.getTicketId()).isEqualTo(foundTicket_2.getTicketId());
        assertThat(ticket_2.getUserId()).isEqualTo(foundTicket_2.getUserId());
        assertThat(ticket_2.getEvent()).isEqualTo(foundTicket_2.getEvent());
        assertThat(ticket_2.getSeat()).isEqualTo(foundTicket_2.getSeat());
        assertThat(ticket_2.getStatus()).isEqualTo(foundTicket_2.getStatus());
    }

    @Test
    @DisplayName("예매 정보로 티켓 조회")
    void ticket_select_by_reservation() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);

        List<Seat> seats = List.of(first, second);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);

        List<Ticket> response = ticketRepository.selectByReservation(reservation);
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("ID로 예매된 티켓 취소")
    void ticket_cancel() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);
        Seat third = this.seatList.get(2);
        Seat forth = this.seatList.get(3);

        List<Seat> seats = List.of(first, second, third, forth);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);

        List<Ticket> response_1 = ticketRepository.selectByReservation(reservation);

        Ticket targetTicket = response_1.get(1); // 2번째 티켓
        assertThat(targetTicket.getStatus()).isEqualTo(TicketStatus.CONFIRMED);

        ticketRepository.cancel(targetTicket.getTicketId()); // 해당 티켓 취소

        Ticket cancelledTicket = ticketRepository.select(targetTicket.getTicketId());
        assertThat(targetTicket.getTicketId()).isEqualTo(cancelledTicket.getTicketId());
        assertThat(cancelledTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("예매 정보로 티켓 취소")
    void ticket_cancel_by_reservation() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event)
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);
        Seat third = this.seatList.get(2);
        Seat forth = this.seatList.get(3);

        List<Seat> seats = List.of(first, second, third, forth);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }

        ticketRepository.insert(tickets);

        List<Ticket> response_1 = ticketRepository.selectByReservation(reservation);

        // 모든 티켓이 예매된 상태인지 확인
        for (Ticket t : response_1) {
            assertThat(t.getStatus()).isEqualTo(TicketStatus.CONFIRMED);
        }

        // 예매 정보로 모든 티켓 취소
        ticketRepository.cancelByReservation(reservation);

        List<Ticket> response_2 = ticketRepository.selectByReservation(reservation);

        // 모든 티켓이 예매 취소된 상태인지 확인
        for (Ticket t : response_2) {
            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        }
    }

    @Test
    @DisplayName("1인 4매 공연에 이미 3매를 예매한 상황에서 추가적으로 티켓팅이 가능한지 확인 (가능)")
    void ticket_is_reservable_case_1() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event) // 최대 1인 4매까지 가능한 공연
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);
        Seat third = this.seatList.get(2);

        List<Seat> seats = List.of(first, second, third);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }
        ticketRepository.insert(tickets);

        // 4. 현재 3매까지 예매한 상태에서 추가적으로 더 예매가 가능한지 확인
        boolean response = ticketRepository.isReservable(userId, this.event);
        assertThat(response).isTrue(); // 3매이므로 추가적으로 예매 가능
    }

    @Test
    @DisplayName("1인 4매 공연에 이미 4매를 예매한 상황에서 추가적으로 티켓팅이 가능한지 확인 (불가능)")
    void ticket_is_reservable_case_2() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event) // 최대 1인 4매까지 가능한 공연
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);
        Seat third = this.seatList.get(2);
        Seat forth = this.seatList.get(3);

        List<Seat> seats = List.of(first, second, third, forth);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }
        ticketRepository.insert(tickets);

        // 4. 현재 4매까지 예매한 상태에서 추가적으로 더 예매가 가능한지 확인
        boolean response = ticketRepository.isReservable(userId, this.event);
        assertThat(response).isFalse(); // 4매이므로 추가적으로 예매 불가능
    }

    @Test
    @DisplayName("1인 4매 공연에 이미 4매를 예매한 상황에서 1장을 취소하고 추가적으로 티켓팅이 가능한지 확인 (가능)")
    void ticket_is_reservable_case_3() throws Exception {
        String userId = "IU";

        // 1. 예매 내역 프레임 만들기
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .event(this.event) // 최대 1인 4매까지 가능한 공연
                .reservedAt(LocalDateTime.now())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationJpaRepository.save(reservation);

        // 2. 좌석 생성
        Seat first = this.seatList.get(0);
        Seat second = this.seatList.get(1);
        Seat third = this.seatList.get(2);
        Seat forth = this.seatList.get(3);

        List<Seat> seats = List.of(first, second, third, forth);

        // 3. 티켓 생성
        List<Ticket> tickets = new ArrayList<>();

        for (Seat seat : seats) {
            Ticket ticket = Ticket.builder()
                    .userId(userId)
                    .reservation(reservation)
                    .event(this.event)
                    .seat(seat)
                    .status(TicketStatus.CONFIRMED)
                    .build();

            tickets.add(ticket);
        }
        ticketRepository.insert(tickets);

        // 4. 현재 4매까지 예매한 상태에서 추가적으로 더 예매가 가능한지 확인
        boolean response_1 = ticketRepository.isReservable(userId, this.event);
        assertThat(response_1).isFalse(); // 4매이므로 추가적으로 예매 불가능

        // 5. 예매된 4장의 티켓 중 한장을 취소.
        List<Ticket> reservedTickets = ticketRepository.selectByReservation(reservation);
        Ticket targetTicket = reservedTickets.get(1); // 취소 시킬 티켓
        ticketRepository.cancel(targetTicket.getTicketId()); // 티켓 취소

        // 6. 1매 취소된 상태에서 추가적으로 더 예매가 가능한지 확인
        boolean response_2 = ticketRepository.isReservable(userId, this.event);
        assertThat(response_2).isTrue(); // 3매이므로 추가적으로 예매 가능
    }
}