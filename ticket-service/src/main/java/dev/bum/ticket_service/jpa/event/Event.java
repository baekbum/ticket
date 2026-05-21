package dev.bum.ticket_service.jpa.event;

import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    // 아티스트 필드 추가
    @Column(nullable = false, length = 100)
    private String artistName;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String venue;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private Integer totalSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    @Column(nullable = false)
    private Integer maxTicketsPerPerson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "event")
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    // 비즈니스 메서드: 생성자 수정
    public Event(InsertEventInfo info) {
        this.artistName = info.getArtistName();
        this.title = info.getTitle();
        if (StringUtils.hasText(info.getDescription())) this.description = info.getDescription();
        this.venue = info.getVenue();
        this.eventDate = info.getEventDate();
        this.totalSeats = info.getTotalSeats();
        this.status = EventStatus.ON_SALE;
        this.maxTicketsPerPerson  = info.getMaxTicketsPerPerson();
        this.seats = new ArrayList<>();
    }

    // 비즈니스 메서드: 수정 로직 추가
    public void update(UpdateEventInfo info) {
        if (StringUtils.hasText(info.getArtistName())) this.artistName = info.getArtistName();
        if (StringUtils.hasText(info.getTitle())) this.title = info.getTitle();
        if (StringUtils.hasText(info.getDescription())) this.description = info.getDescription();
        if (StringUtils.hasText(info.getVenue())) this.venue = info.getVenue();
        if (info.getEventDate() != null) this.eventDate = info.getEventDate();
        if (info.getTotalSeats() != null) this.totalSeats = info.getTotalSeats();
        if (info.getStatus() != null) this.status = info.getStatus();
        if (info.getMaxTicketsPerPerson() != null) this.maxTicketsPerPerson = info.getMaxTicketsPerPerson();
    }
}