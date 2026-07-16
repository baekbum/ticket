package dev.bum.ticket_service.jpa.area;

import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.jpa.event.event.Event;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "areas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "area_name"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "area_id")
    private Long areaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "area_name", nullable = false, length = 80)
    private String areaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatGrade grade;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AreaStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "area")
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    public Area(InsertAreaRequest info, Event event) {
        this.event = event;
        this.areaName = info.getAreaName();
        this.grade = info.getGrade();
        this.price = info.getPrice();
        this.status = info.getStatus() != null ? info.getStatus() : AreaStatus.ACTIVE;
        this.seats = new ArrayList<>();
    }

    public AreaResponse toResponse() {
        return AreaResponse.builder()
                .areaId(this.areaId)
                .eventId(this.event != null ? this.event.getEventId() : null)
                .eventTitle(this.event != null ? this.event.getTitle() : null)
                .areaName(this.areaName)
                .grade(this.grade)
                .price(this.price)
                .status(this.status)
                .build();
    }

    public void update(UpdateAreaRequest info) {
        if (StringUtils.hasText(info.getAreaName())) this.areaName = info.getAreaName();
        if (info.getGrade() != null) this.grade = info.getGrade();
        if (info.getPrice() != null) this.price = info.getPrice();
        if (info.getStatus() != null) this.status = info.getStatus();
    }
}
