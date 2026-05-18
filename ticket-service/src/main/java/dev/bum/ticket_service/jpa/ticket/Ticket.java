package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tickets") // 테이블명도 깔끔하게 tickets
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(nullable = false)
    private String userId;

    // 어떤 예매(주문)에 속한 티켓인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 이 티켓이 가리키는 공연은 어떤 공연인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 이 티켓이 가리키는 좌석은 어디인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TicketStatus status;

    public Ticket(String userId, Reservation reservation, Event event, Seat seat) {
        this.userId = userId;
        this.reservation = reservation;
        this.event = event;
        this.seat = seat;
        this.status = TicketStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }
}
