package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.vo.seat.UpdateSeatAreaConfig;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "zone", "seat_row", "seat_col"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 50)
    private String zone;         // ex) "Floor A", "1층 W"

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;     // ex) 1열, 2열

    @Column(name = "seat_col", nullable = false)
    private Integer seatCol;     // ex) 1번, 2번

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

    // 🌟 빌더 및 생성자 파라미터 수정
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

    // 비즈니스 로직
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