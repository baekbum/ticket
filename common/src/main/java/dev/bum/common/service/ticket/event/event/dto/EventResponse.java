package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {

    // 공연 식별자.
    private Long eventId;

    // 공연 아티스트 이름.
    private String artistName;

    // 제목.
    private String title;

    // 같은 공연의 회차를 묶는 그룹 코드.
    private String eventGroupCode;

    // 상세 설명.
    private String description;

    // 공연장 이름.
    private String venue;

    // 공연장 주소.
    private String venueAddress;

    // 공연 포스터 URL.
    private String posterUrl;

    // 공연 시작 일시.
    private String eventDateTime;

    // 예매 판매 시작 시각.
    private String saleStartAt;

    // 예매 판매 종료 시각.
    private String saleEndAt;

    // 대기열에서 한 번에 입장시킬 인원 수.
    private Integer queueAdmissionSize;

    // 예매 취소 가능 기한.
    private String cancelDeadlineAt;

    // 공연 진행 시간(분).
    private Integer runningMinutes;

    // 관람 가능 최소 연령.
    private Integer ageLimit;

    // 전체 좌석 수.
    private Integer totalSeats;

    // 예매 가능한 좌석 수.
    private Integer availableSeats;

    // 공연 상태.
    private EventStatus status;

    // 사용자별 최대 구매 가능 티켓 수.
    private Integer maxTicketsPerPerson;

    // 구매 수량 제한 적용 범위.
    private TicketLimitScope ticketLimitScope;

    // 공연 장르.
    private EventGenre genre;

    // 공연 지역.
    private EventRegion region;

    // 공연 테마.
    private EventTheme theme;
}
