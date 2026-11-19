package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertEventCommonRequest {

    // 공연 아티스트 이름.
    @NotBlank
    private String artistName;

    // 제목.
    @NotBlank
    private String title;

    // 같은 공연의 회차를 묶는 그룹 코드.
    private String eventGroupCode;

    // 상세 설명.
    private String description;

    // 공연장 이름.
    @NotBlank
    private String venue;

    // 공연장 주소.
    @NotBlank
    private String venueAddress;

    // 공연 포스터 URL.
    private String posterUrl;

    // 공연 진행 시간(분).
    @NotNull
    private Integer runningMinutes;

    // 관람 가능 최소 연령.
    @NotNull
    private Integer ageLimit;

    // 전체 좌석 수.
    @NotNull
    private Integer totalSeats;

    // 사용자별 최대 구매 가능 티켓 수.
    @NotNull
    private Integer maxTicketsPerPerson;

    // 구매 수량 제한 적용 범위.
    private TicketLimitScope ticketLimitScope;

    // 공연 장르.
    @NotNull
    private EventGenre genre;

    // 공연 지역.
    @NotNull
    private EventRegion region;

    // 공연 테마.
    @NotNull
    private EventTheme theme;
}
