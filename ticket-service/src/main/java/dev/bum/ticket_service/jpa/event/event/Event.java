package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    // 🌟 가독성과 성능을 위해 포맷터를 상수로 분리
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String artistName;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String venue;

    @Column(length = 255)
    private String venueAddress;

    @Column(length = 500)
    private String posterUrl;

    @Column(nullable = false)
    private LocalDateTime eventDateTime;

    private LocalDateTime saleStartAt;

    private LocalDateTime saleEndAt;

    private LocalDateTime cancelDeadlineAt;

    private Integer runningMinutes;

    private Integer ageLimit;

    @Column(nullable = false)
    private Integer totalSeats;

    private Integer availableSeats;

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

    @OneToMany(mappedBy = "event")
    @Builder.Default
    private List<Area> areas = new ArrayList<>();

    public EventResponse toResponse() {
        EventStatus.valueOf(this.status.name());
        return EventResponse.builder()
                .eventId(this.eventId)
                .artistName(this.artistName)
                .title(this.title)
                .description(this.description)
                .venue(this.venue)
                .venueAddress(this.venueAddress)
                .posterUrl(this.posterUrl)
                .eventDateTime(this.eventDateTime != null ? this.eventDateTime.format(EVENT_FORMATTER) : null)
                .saleStartAt(this.saleStartAt != null ? this.saleStartAt.format(EVENT_FORMATTER) : null)
                .saleEndAt(this.saleEndAt != null ? this.saleEndAt.format(EVENT_FORMATTER) : null)
                .cancelDeadlineAt(this.cancelDeadlineAt != null ? this.cancelDeadlineAt.format(EVENT_FORMATTER) : null)
                .runningMinutes(this.runningMinutes)
                .ageLimit(this.ageLimit)
                .totalSeats(this.totalSeats)
                .availableSeats(this.availableSeats)
                .status(this.status != null ? EventStatus.valueOf(this.status.name()) : null)
                .maxTicketsPerPerson(this.maxTicketsPerPerson)
                .build();
    }

    // 비즈니스 메서드: 생성자 수정
    public Event(InsertEventRequest info) {
        this.artistName = info.getArtistName();
        this.title = info.getTitle();
        if (StringUtils.hasText(info.getDescription())) this.description = info.getDescription();
        this.venue = info.getVenue();
        if (StringUtils.hasText(info.getVenueAddress())) this.venueAddress = info.getVenueAddress();
        if (StringUtils.hasText(info.getPosterUrl())) this.posterUrl = info.getPosterUrl();
        this.eventDateTime = info.getEventDateTime();
        this.saleStartAt = info.getSaleStartAt();
        this.saleEndAt = info.getSaleEndAt();
        this.cancelDeadlineAt = info.getCancelDeadlineAt();
        this.runningMinutes = info.getRunningMinutes();
        this.ageLimit = info.getAgeLimit();
        this.totalSeats = info.getTotalSeats();
        this.availableSeats = info.getTotalSeats();
        this.status = EventStatus.ON_SALE;
        this.maxTicketsPerPerson  = info.getMaxTicketsPerPerson();
        this.seats = new ArrayList<>();
        this.areas = new ArrayList<>();
    }

    // 비즈니스 메서드: 수정 로직 추가
    public void update(UpdateEventRequest info) {
        if (StringUtils.hasText(info.getArtistName())) this.artistName = info.getArtistName();
        if (StringUtils.hasText(info.getTitle())) this.title = info.getTitle();
        if (StringUtils.hasText(info.getDescription())) this.description = info.getDescription();
        if (StringUtils.hasText(info.getVenue())) this.venue = info.getVenue();
        if (StringUtils.hasText(info.getVenueAddress())) this.venueAddress = info.getVenueAddress();
        if (StringUtils.hasText(info.getPosterUrl())) this.posterUrl = info.getPosterUrl();
        if (info.getEventDateTime() != null) this.eventDateTime = info.getEventDateTime();
        if (info.getSaleStartAt() != null) this.saleStartAt = info.getSaleStartAt();
        if (info.getSaleEndAt() != null) this.saleEndAt = info.getSaleEndAt();
        if (info.getCancelDeadlineAt() != null) this.cancelDeadlineAt = info.getCancelDeadlineAt();
        if (info.getRunningMinutes() != null) this.runningMinutes = info.getRunningMinutes();
        if (info.getAgeLimit() != null) this.ageLimit = info.getAgeLimit();
        if (info.getTotalSeats() != null) this.totalSeats = info.getTotalSeats();
        if (info.getAvailableSeats() != null) this.availableSeats = info.getAvailableSeats();
        if (info.getStatus() != null) this.status = info.getStatus();
        if (info.getMaxTicketsPerPerson() != null) this.maxTicketsPerPerson = info.getMaxTicketsPerPerson();
    }

    public void updatePosterUrl(String posterUrl) {
        if (StringUtils.hasText(posterUrl)) this.posterUrl = posterUrl;
    }
}
