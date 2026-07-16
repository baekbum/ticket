package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.exception.ticket.TicketNotExistException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(TicketRepositoryImpl.class)
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TicketRepositoryImplTest {

    @Autowired
    private TicketRepositoryImpl ticketRepository;

    @Autowired
    private TicketJpaRepository ticketJpaRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private AreaJpaRepository areaJpaRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Event event;
    private Reservation reservation;
    private List<Seat> seatList;

    @BeforeEach
    void setUp() {
        event = eventJpaRepository.save(event());
        Area area = areaJpaRepository.save(area(event));
        seatList = seatJpaRepository.saveAll(List.of(
                seat(event, area, "VIP", 1, 1),
                seat(event, area, "VIP", 1, 2),
                seat(event, area, "VIP", 1, 3),
                seat(event, area, "VIP", 1, 4),
                seat(event, area, "VIP", 1, 5)
        ));
        reservation = reservationJpaRepository.save(reservation("order-1", "user01", event));
        entityManager.flush();
        entityManager.clear();

        event = eventJpaRepository.findById(event.getEventId()).orElseThrow();
        reservation = reservationJpaRepository.findById(reservation.getReservationId()).orElseThrow();
        seatList = seatJpaRepository.findByEventEventId(event.getEventId());
    }

    @Test
    @DisplayName("티켓 등록")
    void ticket_insert() {
        List<Ticket> tickets = List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1))
        );

        ticketRepository.insert(tickets);
        entityManager.flush();
        entityManager.clear();

        List<Ticket> response = ticketJpaRepository.findByReservation(reservation);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(Ticket::getStatus).containsOnly(TicketStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("ID로 티켓 조회")
    void ticket_select_by_id() {
        Ticket saved = ticketJpaRepository.save(ticket(reservation, event, seatList.get(0)));
        entityManager.flush();
        entityManager.clear();

        Ticket response = ticketRepository.select(saved.getTicketId());

        assertThat(response.getTicketId()).isEqualTo(saved.getTicketId());
        assertThat(response.getUserId()).isEqualTo("user01");
        assertThat(response.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 티켓 조회 시 예외 발생")
    void ticket_select_by_id_fail() {
        assertThatThrownBy(() -> ticketRepository.select(999L))
                .isInstanceOf(TicketNotExistException.class);
    }

    @Test
    @DisplayName("ID 목록으로 티켓 조회")
    void ticket_select_by_id_list() {
        List<Ticket> tickets = ticketJpaRepository.saveAll(List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1))
        ));
        entityManager.flush();
        entityManager.clear();
        List<Long> ids = tickets.stream().map(Ticket::getTicketId).toList();

        List<Ticket> response = ticketRepository.selectByIdList(ids);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(Ticket::getTicketId).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    @DisplayName("빈 ID 목록으로 티켓 조회 시 예외 발생")
    void ticket_select_by_id_list_fail() {
        assertThatThrownBy(() -> ticketRepository.selectByIdList(List.of(999L)))
                .isInstanceOf(TicketNotExistException.class);
    }

    @Test
    @DisplayName("예약 정보로 티켓 조회")
    void ticket_select_by_reservation() {
        ticketRepository.insert(List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1))
        ));
        entityManager.flush();
        entityManager.clear();
        Reservation foundReservation = reservationJpaRepository.findById(reservation.getReservationId()).orElseThrow();

        List<Ticket> response = ticketRepository.selectByReservation(foundReservation);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(Ticket::getUserId).containsOnly("user01");
    }

    @Test
    @DisplayName("티켓이 없는 예약 정보로 조회 시 예외 발생")
    void ticket_select_by_reservation_fail() {
        assertThatThrownBy(() -> ticketRepository.selectByReservation(reservation))
                .isInstanceOf(TicketNotExistException.class);
    }

    @Test
    @DisplayName("사용자 예매 수량이 제한보다 적으면 추가 예매 가능")
    void ticket_is_within_purchase_limit() {
        ticketRepository.insert(List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1)),
                ticket(reservation, event, seatList.get(2))
        ));
        entityManager.flush();
        entityManager.clear();

        boolean response = ticketRepository.isWithinPurchaseLimit("user01", event, 1);

        assertThat(response).isTrue();
    }

    @Test
    @DisplayName("사용자 예매 수량이 제한과 같으면 추가 예매 불가")
    void ticket_is_not_within_purchase_limit_when_already_max() {
        ticketRepository.insert(List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1)),
                ticket(reservation, event, seatList.get(2)),
                ticket(reservation, event, seatList.get(3))
        ));
        entityManager.flush();
        entityManager.clear();

        boolean response = ticketRepository.isWithinPurchaseLimit("user01", event, 1);

        assertThat(response).isFalse();
    }

    @Test
    @DisplayName("취소된 티켓은 사용자 예매 수량 계산에서 제외")
    void ticket_is_within_purchase_limit_excludes_cancelled_ticket() {
        List<Ticket> tickets = ticketJpaRepository.saveAll(List.of(
                ticket(reservation, event, seatList.get(0)),
                ticket(reservation, event, seatList.get(1)),
                ticket(reservation, event, seatList.get(2)),
                ticket(reservation, event, seatList.get(3))
        ));
        tickets.get(0).cancel();
        entityManager.flush();
        entityManager.clear();

        boolean response = ticketRepository.isWithinPurchaseLimit("user01", event, 1);

        assertThat(response).isTrue();
    }

    private Ticket ticket(Reservation reservation, Event event, Seat seat) {
        return Ticket.builder()
                .userId("user01")
                .reservation(reservation)
                .event(event)
                .seat(seat)
                .status(TicketStatus.PENDING_PAYMENT)
                .build();
    }

    private Reservation reservation(String orderId, String userId, Event event) {
        return Reservation.builder()
                .orderId(orderId)
                .userId(userId)
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Seat seat(Event event, Area area, String zone, Integer row, Integer col) {
        return Seat.builder()
                .event(event)
                .area(area)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.RESERVED)
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
