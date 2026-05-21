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
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Builder
    public Ticket(Long ticketId, String userId, Reservation reservation, Event event, Seat seat, TicketStatus status) {
        this.ticketId = ticketId;
        this.userId = userId;
        this.event = event;
        this.seat = seat;
        this.status = status != null ? status : TicketStatus.READY_TO_PAY;

        if (reservation != null) {
            changeReservation(reservation);
        }
    }

    public Ticket(String userId, Reservation reservation, Event event, Seat seat) {
        this.userId = userId;
        this.event = event;
        this.seat = seat;
        this.status = TicketStatus.READY_TO_PAY;

        if (reservation != null) {
            changeReservation(reservation);
        }
    }

    /**
     * Reservation 연관관계 편의 메서드
     */
    private void changeReservation(Reservation reservation) {
        this.reservation = reservation;
        // 부모 객체의 리스트가 null이 아니라면 나 자신을 리스트에 추가
        if (reservation.getTickets() != null) {
            reservation.getTickets().add(this);
        }
    }

    /**
     * 무통장 입금 대기 상태로 변경 (가상계좌 발급 시)
     */
    public void awaitingDeposit() {
        this.status = TicketStatus.AWAITING_DEPOSIT;
    }

    /**
     * 결제 완료 상태로 변경 (카드 승인 또는 무통장 입금 확인 시)
     */
    public void completePayment() {
        this.status = TicketStatus.PAYMENT_COMPLETED;
    }

    /**
     * 예매/결제 취소 상태로 변경 (미입금 만료 또는 유저 취소 시)
     */
    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }
}
