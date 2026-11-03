package dev.bum.common.service.ticket.event.eventLayout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventLayoutResponse {

    // 좌석 배치도 식별자.
    private Long layoutId;

    // 공연 식별자.
    private Long eventId;

    // 업로드한 원본 파일 이름.
    private String originalFileName;

    // SVG 좌석 배치도 내용.
    private String svgText;
}
