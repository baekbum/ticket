package dev.bum.ticket_service.jpa.seat;

import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "zone", "seat_row", "seat_col"})
)
@Getter
@NoArgsConstructor
public class Seat {

    // 🌟 날짜 포맷 최적화를 위한 포맷터 상수화
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 50)
    private String zone;

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "seat_col", nullable = false)
    private Integer seatCol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatGrade grade;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeatStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SeatResponse toDto() {
        return SeatResponse.builder()
                .seatId(this.seatId)
                .zone(this.zone)
                .seatRow(this.seatRow)
                .seatCol(this.seatCol)
                .seatName(String.format("%s %d열 %d번", this.zone, this.seatRow, this.seatCol))
                .grade(this.grade)
                .price(this.price)
                .status(this.status)
                // 연관관계 Event 데이터 조립 (N+1 고려 필수, 서비스단 fetch join 권장)
                .eventId(this.event != null ? this.event.getEventId() : null)
                .artistName(this.event != null ? this.event.getArtistName() : null)
                .title(this.event != null ? this.event.getTitle() : null)
                .venue(this.event != null ? this.event.getVenue() : null)
                .eventDateTime(this.event != null && this.event.getEventDateTime() != null ?
                        this.event.getEventDateTime().format(EVENT_FORMATTER) : null)
                .build();
    }

    @Builder
    public Seat(Long seatId, Event event, String zone, Integer seatRow, Integer seatCol, SeatGrade grade, Integer price, SeatStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.seatId = seatId;
        this.zone = zone;
        this.seatRow = seatRow;
        this.seatCol = seatCol;
        this.grade = grade;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        if (event != null) {
            changeEvent(event);
        }
    }

    /**
     * Event 연관관계 편의 메서드
     */
    private void changeEvent(Event event) {
        this.event = event;
        if (event.getSeats() != null) {
            event.getSeats().add(this);
        }
    }

    public void update(UpdateSeatAreaConfig config) {
        if (config.getPrice() != null) this.price = config.getPrice();
        if (config.getStatus() != null) this.status = config.getStatus();
    }

    public void available() {
        this.status = SeatStatus.AVAILABLE;
    }

    public void lock() {
        this.status = SeatStatus.LOCKED;
    }

    public void reserved() {
        this.status = SeatStatus.RESERVED;
    }
}