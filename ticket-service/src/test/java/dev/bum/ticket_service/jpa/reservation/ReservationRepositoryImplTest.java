package dev.bum.ticket_service.jpa.reservation;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.exception.reservation.ReservationNotExistException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.area.AreaRepositoryImpl;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepositoryImpl;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.jpa.ticket.TicketRepositoryImpl;
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
        ReservationRepositoryImpl.class,
        TicketRepositoryImpl.class,
        SeatRepositoryImpl.class,
        AreaRepositoryImpl.class,
        EventRepositoryImpl.class,
        QuerydslConfig.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ReservationRepositoryImplTest {

    @Autowired
    private ReservationRepositoryImpl reservationRepository;

    @Autowired
    private SeatRepositoryImpl seatRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Autowired
    private AreaJpaRepository areaJpaRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private TicketJpaRepository ticketJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private Event event;
    private Area area;
    private List<Seat> seatList;

    @BeforeEach
    void setUp() {
        event = eventJpaRepository.save(event("IU", "IU Concert"));
        area = areaJpaRepository.save(area(event, "VIP"));
        seatRepository.insert(insertSeatRequest(event, area, "VIP", 2, 3));
        entityManager.flush();
        entityManager.clear();

        seatList = seatJpaRepository.findAll();
    }

    @Test
    @DisplayName("예약 등록")
    void reservation_insert() {
        InsertReservationRequest info = insertReservationRequest("order-1", "user01", event, seatList.subList(0, 2));

        Reservation saved = reservationRepository.insert(info);
        entityManager.flush();
        entityManager.clear();

        Reservation response = reservationRepository.selectById(saved.getReservationId());
        List<Ticket> tickets = ticketJpaRepository.findByReservation(response);

        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getUserId()).isEqualTo("user01");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(tickets).hasSize(2);
        assertThat(tickets).extracting(Ticket::getStatus).containsOnly(TicketStatus.PENDING_PAYMENT);
        assertThat(tickets).extracting(ticket -> ticket.getSeat().getStatus()).containsOnly(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("최대 예매 매수보다 많은 좌석을 선택하면 예외 발생")
    void reservation_insert_fail_over_limit() {
        InsertReservationRequest info = insertReservationRequest("order-1", "user01", event, seatList.subList(0, 5));

        assertThatThrownBy(() -> reservationRepository.insert(info))
                .isInstanceOf(TicketLimitExceededException.class);
    }

    @Test
    @DisplayName("기존 예매 수량과 추가 예매 수량 합계가 제한을 넘으면 예외 발생")
    void reservation_insert_fail_when_purchase_limit_exceeded() {
        reservationRepository.insert(insertReservationRequest("order-1", "user01", event, seatList.subList(0, 3)));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> reservationRepository.insert(
                insertReservationRequest("order-2", "user01", event, seatList.subList(3, 5))
        )).isInstanceOf(TicketLimitExceededException.class);
    }

    @Test
    @DisplayName("ID로 예약 조회")
    void reservation_select_by_id() {
        Reservation saved = reservationRepository.insert(
                insertReservationRequest("order-1", "user01", event, seatList.subList(0, 2))
        );
        entityManager.flush();
        entityManager.clear();

        Reservation response = reservationRepository.selectById(saved.getReservationId());

        assertThat(response.getReservationId()).isEqualTo(saved.getReservationId());
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getEvent().getEventId()).isEqualTo(event.getEventId());
        assertThat(response.getTickets()).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 예약 조회 시 예외 발생")
    void reservation_select_by_id_fail() {
        assertThatThrownBy(() -> reservationRepository.selectById(999L))
                .isInstanceOf(ReservationNotExistException.class);
    }

    @Test
    @DisplayName("조건으로 예약 조회")
    void reservation_select_by_cond() {
        Reservation firstReservation = reservationRepository.insert(
                insertReservationRequest("order-1", "user01", event, seatList.subList(0, 2))
        );
        Event anotherEvent = eventJpaRepository.save(event("AKMU", "AKMU Concert"));
        Area anotherArea = areaJpaRepository.save(area(anotherEvent, "R"));
        seatRepository.insert(insertSeatRequest(anotherEvent, anotherArea, "R", 1, 2));
        entityManager.flush();
        entityManager.clear();

        List<Seat> anotherSeats = seatJpaRepository.findByEventEventId(anotherEvent.getEventId());
        Reservation secondReservation = reservationRepository.insert(
                insertReservationRequest("order-2", "user01", anotherEvent, anotherSeats)
        );
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("reservationId").descending());
        ReservationCondRequest userCond = ReservationCondRequest.builder()
                .userId("user01")
                .build();
        ReservationCondRequest eventCond = ReservationCondRequest.builder()
                .userId("user01")
                .eventId(anotherEvent.getEventId())
                .build();

        Page<Reservation> userResponse = reservationRepository.selectByCond(userCond, pageable);
        Page<Reservation> eventResponse = reservationRepository.selectByCond(eventCond, pageable);

        assertThat(userResponse.getTotalElements()).isEqualTo(2);
        assertThat(userResponse.getContent()).extracting(Reservation::getReservationId)
                .containsExactly(secondReservation.getReservationId(), firstReservation.getReservationId());
        assertThat(eventResponse.getTotalElements()).isEqualTo(1);
        assertThat(eventResponse.getContent().get(0).getEvent().getEventId()).isEqualTo(anotherEvent.getEventId());
    }

    @Test
    @DisplayName("전체 예약 취소")
    void reservation_cancel_all() {
        Reservation saved = reservationRepository.insert(
                insertReservationRequest("order-1", "user01", event, seatList.subList(0, 3))
        );
        entityManager.flush();
        entityManager.clear();
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(event.getEventId())
                .selectedTicketIdList(new ArrayList<>())
                .build();

        List<Seat> cancelledSeats = reservationRepository.cancel(saved.getReservationId(), info);
        entityManager.flush();
        entityManager.clear();

        Reservation response = reservationRepository.selectById(saved.getReservationId());
        List<Ticket> tickets = ticketJpaRepository.findByReservation(response);

        assertThat(cancelledSeats).hasSize(3);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(tickets).extracting(Ticket::getStatus).containsOnly(TicketStatus.CANCELLED);
        assertThat(tickets).extracting(ticket -> ticket.getSeat().getStatus()).containsOnly(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("일부 티켓 취소")
    void reservation_cancel_some() {
        Reservation saved = reservationRepository.insert(
                insertReservationRequest("order-1", "user01", event, seatList.subList(0, 3))
        );
        entityManager.flush();
        entityManager.clear();
        Reservation reservation = reservationRepository.selectById(saved.getReservationId());
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(event.getEventId())
                .selectedTicketIdList(List.of(tickets.get(0).getTicketId()))
                .build();

        List<Seat> cancelledSeats = reservationRepository.cancel(saved.getReservationId(), info);
        entityManager.flush();
        entityManager.clear();

        Reservation response = reservationRepository.selectById(saved.getReservationId());
        List<Ticket> responseTickets = ticketJpaRepository.findByReservation(response);

        assertThat(cancelledSeats).hasSize(1);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(responseTickets).extracting(Ticket::getStatus)
                .containsExactlyInAnyOrder(TicketStatus.CANCELLED, TicketStatus.PENDING_PAYMENT, TicketStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("예약 가능 여부 확인")
    void reservation_validate_reservable_from_database() {
        reservationRepository.validateReservableFromDatabase("user01", event.getEventId(), 2);
    }

    @Test
    @DisplayName("예약 가능 여부 확인 시 선택 좌석 수가 제한을 넘으면 예외 발생")
    void reservation_validate_reservable_from_database_fail_over_limit() {
        assertThatThrownBy(() -> reservationRepository.validateReservableFromDatabase("user01", event.getEventId(), 5))
                .isInstanceOf(TicketLimitExceededException.class);
    }

    private InsertReservationRequest insertReservationRequest(String orderId, String userId, Event event, List<Seat> seats) {
        List<SeatInfo> seatInfos = seats.stream()
                .map(seat -> SeatInfo.builder()
                        .id(seat.getSeatId())
                        .zone(seat.getZone())
                        .row(seat.getSeatRow())
                        .col(seat.getSeatCol())
                        .build())
                .toList();

        return InsertReservationRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .eventId(event.getEventId())
                .seats(seatInfos)
                .build();
    }

    private InsertSeatRequest insertSeatRequest(Event event, Area area, String zone, int rows, int cols) {
        return InsertSeatRequest.builder()
                .eventId(event.getEventId())
                .areaId(area.getAreaId())
                .insertSeatAreaConfigs(List.of(InsertSeatAreaConfig.builder()
                        .grade(area.getGrade())
                        .zone(zone)
                        .rows(rows)
                        .cols(cols)
                        .price(area.getPrice())
                        .startX(10D)
                        .startY(20D)
                        .seatWidth(14D)
                        .seatHeight(14D)
                        .gapX(4D)
                        .gapY(4D)
                        .build()))
                .build();
    }

    private Event event(String artistName, String title) {
        return Event.builder()
                .artistName(artistName)
                .title(title)
                .description(title + " description")
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

    private Area area(Event event, String areaName) {
        return Area.builder()
                .event(event)
                .areaName(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }
}
