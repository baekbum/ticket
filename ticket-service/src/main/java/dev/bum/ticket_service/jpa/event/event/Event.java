package dev.bum.ticket_service.jpa.event.event;

import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.reservation.policy.ReservationCancellationPolicy;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.seat.Seat;

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

    // 공연 일시 표시 형식.
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분");

    // 공연 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    // 공연 아티스트 이름.
    @Column(nullable = false, length = 100)
    private String artistName;

    // 제목.
    @Column(nullable = false, length = 100)
    private String title;

    // 같은 공연의 회차를 묶는 그룹 코드.
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String eventGroupCode = "DEFAULT_EVENT_GROUP";

    // 상세 설명.
    @Column(columnDefinition = "TEXT")
    private String description;

    // 공연장 이름.
    @Column(nullable = false, length = 100)
    private String venue;

    // 공연장 주소.
    @Column(length = 255)
    private String venueAddress;

    // 공연 포스터 URL.
    @Column(length = 500)
    private String posterUrl;

    // 공연 시작 일시.
    @Column(nullable = false)
    private LocalDateTime eventDateTime;

    // 예매 판매 시작 시각.
    private LocalDateTime saleStartAt;

    // 예매 판매 종료 시각.
    private LocalDateTime saleEndAt;

    // 대기열에서 한 번에 입장시킬 인원 수.
    @Column(nullable = false, columnDefinition = "integer default 100")
    @Builder.Default
    private int queueAdmissionSize = 100;

    // 예매 취소 가능 기한.
    private LocalDateTime cancelDeadlineAt;

    // 공연 진행 시간(분).
    private Integer runningMinutes;

    // 관람 가능 최소 연령.
    private Integer ageLimit;

    // 전체 좌석 수.
    @Column(nullable = false)
    private Integer totalSeats;

    // 예매 가능한 좌석 수.
    private Integer availableSeats;

    // 공연 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    // 사용자별 최대 구매 가능 티켓 수.
    @Column(nullable = false)
    private Integer maxTicketsPerPerson;

    // 구매 수량 제한 적용 범위.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TicketLimitScope ticketLimitScope = TicketLimitScope.PER_EVENT;

    // 공연 장르.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventGenre genre = EventGenre.CONCERT;

    // 공연 지역.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventRegion region = EventRegion.SEOUL;

    // 공연 테마.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventTheme theme = EventTheme.IDOL;

    // 생성 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 좌석 목록.
    @OneToMany(mappedBy = "event")
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    // 좌석 구역 목록.
    @OneToMany(mappedBy = "event")
    @Builder.Default
    private List<Area> areas = new ArrayList<>();

    // 예매 취소 가능 기한 값을 반환한다.
    public LocalDateTime getCancelDeadlineAt() {

        return ReservationCancellationPolicy.effectiveDeadline(cancelDeadlineAt);
    }

    // 처리 상태를 변경한다.
    public void changeStatus(EventStatus status) {

        this.status = status; }

    // 외부 응답에 사용할 DTO로 변환한다.
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
                .queueAdmissionSize(queueAdmissionSize)
                .cancelDeadlineAt(getCancelDeadlineAt() != null ? getCancelDeadlineAt().format(EVENT_FORMATTER) : null)
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

    // 공연을 수정한다.
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

        if (info.getQueueAdmissionSize() != null) {
            if (info.getQueueAdmissionSize() <= 0) throw new IllegalArgumentException("활성 인원 상한은 양수여야 합니다.");
            this.queueAdmissionSize = info.getQueueAdmissionSize();
        }

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

    // 공연 포스터 URL을 변경한다.
    public void updatePosterUrl(String posterUrl) {

        if (StringUtils.hasText(posterUrl)) this.posterUrl = posterUrl;
    }
}
