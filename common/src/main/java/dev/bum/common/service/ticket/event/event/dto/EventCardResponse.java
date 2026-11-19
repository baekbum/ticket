package dev.bum.common.service.ticket.event.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCardResponse {

    // 같은 공연의 회차를 묶는 그룹 코드.
    private String eventGroupCode;

    // 공연 아티스트 이름.
    private String artistName;

    // 제목.
    private String title;

    // 공연 포스터 URL.
    private String posterUrl;

    // 공연장 이름.
    private String venue;

    // 공연 시작일.
    private LocalDate eventStartDate;

    // 공연 종료일.
    private LocalDate eventEndDate;
}
