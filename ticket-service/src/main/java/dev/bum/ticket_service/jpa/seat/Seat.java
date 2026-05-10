package dev.bum.ticket_service.jpa.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 20)
    private String seatNumber;  // ex) "A-12", "VIP-03"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatGrade grade;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SeatStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Seat(InsertSeatInfo info) {
        this.event = info.getEvent();
        this.seatNumber = info.getSeatNumber();
        this.grade = info.getGrade();
        this.price = info.getPrice();
        this.status = SeatStatus.RESERVED;
    }

    public void update(UpdateSeatInfo info) {
        if (StringUtils.hasText(info.getSeatNumber())) this.seatNumber = info.getSeatNumber();
        if (info.getGrade() != null) this.grade = info.getGrade();
        if (info.getPrice() != null) this.price = info.getPrice();
        if (info.getStatus() != null) this.status = info.getStatus();
    }
}
