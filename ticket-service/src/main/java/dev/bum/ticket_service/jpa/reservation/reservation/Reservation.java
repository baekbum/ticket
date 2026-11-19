package dev.bum.ticket_service.jpa.reservation.reservation;

import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
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
import jakarta.persistence.OneToOne;
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

    // 예매 일시 표시 형식.
    private static final DateTimeFormatter RESERVED_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    // 공연 일시 표시 형식.
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");

    // 예매 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    // 주문 식별자.
    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

    // 사용자 ID.
    @Column(nullable = false)
    private String userId;

    // 공연 또는 발행 이벤트 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 예매 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    // 티켓 목록.
    @OneToMany(mappedBy = "reservation")
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    // 배송 정보.
    @OneToOne(mappedBy = "reservation", fetch = FetchType.LAZY)
    private ReservationDelivery delivery;

    // 예매 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime reservedAt;

    // 생성 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 외부 응답에 사용할 DTO로 변환한다.
    public ReservationResponse toResponse() {

        return ReservationResponse.builder()
                .reservationId(this.reservationId)
                .orderId(this.orderId)
                .userId(this.userId)
                .eventId(this.event != null ? this.event.getEventId() : 0L)
                .eventTitle(this.event != null ? this.event.getTitle() : null)
                .posterUrl(this.event != null ? this.event.getPosterUrl() : null)
                .cancelDeadlineAt(this.event != null && this.event.getCancelDeadlineAt() != null
                        ? this.event.getCancelDeadlineAt().toString() : null)
                .reservedDate(this.reservedAt != null ? this.reservedAt.format(RESERVED_FORMATTER) : null)
                .eventDateTime(this.event != null && this.event.getEventDateTime() != null ?
                        this.event.getEventDateTime().format(EVENT_FORMATTER) : null)
                .venue(this.event != null ? this.event.getVenue() : null)
                .ticketCount(this.tickets != null ? this.tickets.size() : 0)
                .status(this.status != null ? this.status.name() : null)
                .build();
    }

    // 예매 처리에서 결제 완료 상태로 변경한다.
    public void paid() {

        this.status = ReservationStatus.PAID;
    }

    // 예매를 취소 상태로 변경한다.
    public void cancel() {

        this.status = ReservationStatus.CANCELLED;
    }

    // 예매를 부분 취소 상태로 변경한다.
    public void partial_cancel() {

        this.status = ReservationStatus.PARTIALLY_CANCELLED;
    }

    // 예매를 만료 상태로 변경한다.
    public void expire() {

        this.status = ReservationStatus.EXPIRED;
    }

    // 예매 처리에서 결제 대기 상태로 변경한다.
    public void pendingPayment() {

        this.status = ReservationStatus.PENDING_PAYMENT;
    }
}
