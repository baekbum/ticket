package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.seat.Seat;
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

    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String artistName;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String eventGroupCode = "DEFAULT_EVENT_GROUP";

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TicketLimitScope ticketLimitScope = TicketLimitScope.PER_EVENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventGenre genre = EventGenre.CONCERT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventRegion region = EventRegion.SEOUL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventTheme theme = EventTheme.IDOL;

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
                .eventGroupCode(this.eventGroupCode)
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
                .ticketLimitScope(this.ticketLimitScope)
                .genre(this.genre)
                .region(this.region)
                .theme(this.theme)
                .build();
    }

    // 비즈니스 메서드: 수정 로직 추가
    public void update(UpdateEventRequest info) {
        if (StringUtils.hasText(info.getArtistName())) this.artistName = info.getArtistName();
        if (StringUtils.hasText(info.getTitle())) this.title = info.getTitle();
        if (StringUtils.hasText(info.getEventGroupCode())) this.eventGroupCode = info.getEventGroupCode();
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
        if (info.getTicketLimitScope() != null) this.ticketLimitScope = info.getTicketLimitScope();
        if (info.getGenre() != null) this.genre = info.getGenre();
        if (info.getRegion() != null) this.region = info.getRegion();
        if (info.getTheme() != null) this.theme = info.getTheme();
    }

    public void updatePosterUrl(String posterUrl) {
        if (StringUtils.hasText(posterUrl)) this.posterUrl = posterUrl;
    }
}
