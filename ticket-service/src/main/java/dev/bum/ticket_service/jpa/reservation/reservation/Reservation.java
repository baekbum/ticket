package dev.bum.ticket_service.jpa.reservation.reservation;

import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.ticket.Ticket;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservations_order_id", columnNames = "order_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    private static final DateTimeFormatter RESERVED_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

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

    public ReservationResponse toResponse() {
        return ReservationResponse.builder()
                .reservationId(this.reservationId)
                .orderId(this.orderId)
                .userId(this.userId)
                .eventId(this.event != null ? this.event.getEventId() : 0L)
                .eventTitle(this.event != null ? this.event.getTitle() : null)
                .reservedDate(this.reservedAt != null ? this.reservedAt.format(RESERVED_FORMATTER) : null)
                .eventDateTime(this.event != null && this.event.getEventDateTime() != null ?
                        this.event.getEventDateTime().format(EVENT_FORMATTER) : null)
                .venue(this.event != null ? this.event.getVenue() : null)
                .ticketCount(this.tickets != null ? this.tickets.size() : 0)
                .status(this.status != null ? this.status.name() : null)
                .build();
    }

    public Reservation(InsertReservationRequest info, Event event) {
        this.orderId = info.getOrderId();
        this.userId = info.getUserId();
        this.event = event;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.tickets = new ArrayList<>();
        this.reservedAt = LocalDateTime.now();
    }

    public void paid() {
        this.status = ReservationStatus.PAID;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void partial_cancel() {
        this.status = ReservationStatus.PARTIALLY_CANCELLED;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
}
