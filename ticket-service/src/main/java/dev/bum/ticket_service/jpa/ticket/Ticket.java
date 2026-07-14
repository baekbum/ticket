package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.Event;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(nullable = false)
    private Integer price;

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

    @Builder
    public Ticket(Long ticketId, String userId, Reservation reservation, Event event, Seat seat, TicketStatus status) {
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

    public Ticket(String userId, Reservation reservation, Event event, Seat seat) {
        this.userId = userId;
        this.event = event;
        this.seat = seat;
        this.status = TicketStatus.PENDING_PAYMENT;
        this.price = resolvePrice(seat);

        if (reservation != null) {
            changeReservation(reservation);
        }
    }

    private void changeReservation(Reservation reservation) {
        this.reservation = reservation;
        if (reservation.getTickets() != null) {
            reservation.getTickets().add(this);
        }
    }

    private Integer resolvePrice(Seat seat) {
        return seat != null && seat.getPrice() != null ? seat.getPrice() : 0;
    }

    public void paid() {
        this.status = TicketStatus.PAID;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public void expire() {
        this.status = TicketStatus.EXPIRED;
    }
}
