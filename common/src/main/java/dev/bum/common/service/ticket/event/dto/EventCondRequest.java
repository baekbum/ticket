package dev.bum.common.service.ticket.event.dto;

import dev.bum.common.service.ticket.event.enums.EventStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCondRequest {

    private Long eventId;
    private String artistName;
    private String title;
    private String venue;
    private LocalDate eventDate;
    private EventStatus status;

    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시

    @Override
    public String toString() {
        // 🌟 콤마(,) 조립을 편하게 하기 위해 StringJoiner 활용 (EventCond 형태로 시작)
        StringJoiner sj = new StringJoiner(", ", "EventCond{", "}");

        // 필수 필드 (기본값이 항상 존재하므로 무조건 포함)
        sj.add("page=" + page);
        sj.add("size=" + size);

        // 검색 조건 필드 검증 (타입에 맞춰 문자열은 작은따옴표 처리)
        if (eventId != null) sj.add("eventId=" + eventId);
        if (artistName != null) sj.add("artistName='" + artistName + "'");
        if (title != null) sj.add("title='" + title + "'");
        if (venue != null) sj.add("venue='" + venue + "'");
        if (eventDate != null) sj.add("eventDate=" + eventDate);
        if (status != null) sj.add("status=" + status);

        // 정렬 조건 필드 검증 (List 포맷팅 적용)
        if (sort != null && !sort.isEmpty()) {
            sj.add("sort=[" + String.join(", ", sort) + "]");
        }

        return sj.toString();
    }
}
