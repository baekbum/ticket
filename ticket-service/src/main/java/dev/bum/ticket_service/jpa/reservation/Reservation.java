package dev.bum.ticket_service.jpa.reservation;

import dev.bum.ticket_service.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation")
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime reservedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    public Reservation(InsertReservationInfo info) {
        this.userId = info.getUserId();
        this.event = info.getEvent();
        this.status = ReservationStatus.CONFIRMED;
        this.tickets = new ArrayList<>();
        this.reservedAt = LocalDateTime.now();
    }

    // 비즈니스 메서드
    public void confirmed() {
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void partial_cancel() {
        this.status = ReservationStatus.PARTIALLY_CANCELLED;
    }
}