package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor
public class Ticket {

    // 티켓 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    // 사용자 ID.
    @Column(nullable = false)
    private String userId;

    // 예매 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 공연 또는 발행 이벤트 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 좌석 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    // 티켓 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    // 가격.
    @Column(nullable = false)
    private Integer price;

    // 외부 응답에 사용할 DTO로 변환한다.
    public TicketResponse toResponse() {

        return TicketResponse.builder()
                .ticketId(this.ticketId)
                .seatId(this.seat != null ? this.seat.getSeatId() : null)
                .zone(this.seat != null ? this.seat.getZone() : null)
                .seatRow(this.seat != null ? this.seat.getSeatRow() : null)
                .seatCol(this.seat != null ? this.seat.getSeatCol() : null)
                .seatName(this.seat != null ?
                        String.format("%s %d열 %d번", this.seat.getZone(), this.seat.getSeatRow(), this.seat.getSeatCol()) : null)
                .grade(this.seat != null && this.seat.getGrade() != null ? this.seat.getGrade().name() : null)
                .price(this.price)
                .status(this.status != null ? this.status.name() : null)
                .build();
    }

    // 전달받은 값으로 티켓 객체를 초기화한다.
    @Builder
    private Ticket(Long ticketId, String userId, Reservation reservation, Event event, Seat seat, TicketStatus status) {

        this.ticketId = ticketId;
        this.userId = userId;
        this.event = event;
        this.seat = seat;
        this.status = status != null ? status : TicketStatus.PENDING_PAYMENT;
        this.price = resolvePrice(seat);

        if (reservation != null) {
            changeReservation(reservation);
        }
    }

    // 티켓에 연결된 예매 정보를 변경한다.
    private void changeReservation(Reservation reservation) {

        this.reservation = reservation;
        if (reservation.getTickets() != null) {
            reservation.getTickets().add(this);
        }
    }

    // 좌석과 요청 설정에 적용할 가격을 결정한다.
    private Integer resolvePrice(Seat seat) {

        return seat != null && seat.getPrice() != null ? seat.getPrice() : 0;
    }

    // 티켓 처리에서 결제 완료 상태로 변경한다.
    public void paid() {

        this.status = TicketStatus.PAID;
    }

    // 티켓을 취소 상태로 변경한다.
    public void cancel() {

        this.status = TicketStatus.CANCELLED;
    }

    // 티켓을 만료 상태로 변경한다.
    public void expire() {

        this.status = TicketStatus.EXPIRED;
    }

    // 티켓 처리에서 결제 대기 상태로 변경한다.
    public void pendingPayment() {

        this.status = TicketStatus.PENDING_PAYMENT;
    }
}
