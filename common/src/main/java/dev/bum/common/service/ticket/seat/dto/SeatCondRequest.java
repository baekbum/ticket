package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import lombok.*;

import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatCondRequest {

    private Long seatId;
    private Long eventId;
    private String zone;
    private Integer seatRow;
    private Integer seatCol;
    private SeatGrade grade;
    private SeatStatus status;

    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시

    @Override
    public String toString() {
        // 🌟 콤마(,) 조립을 편하게 하기 위해 StringJoiner 활용 (EventCond 형태로 시작)
        StringJoiner sj = new StringJoiner(", ", "SeatCond{", "}");

        // 필수 필드 (기본값이 항상 존재하므로 무조건 포함)
        sj.add("page=" + page);
        sj.add("size=" + size);

        // 검색 조건 필드 검증 (타입에 맞춰 문자열은 작은따옴표 처리)
        if (seatId != null) sj.add("seatId=" + seatId);
        if (eventId != null) sj.add("eventId='" + eventId + "'");
        if (zone != null) sj.add("zone='" + zone + "'");
        if (seatRow != null) sj.add("seatRow='" + seatRow + "'");
        if (seatCol != null) sj.add("seatCol='" + seatCol + "'");
        if (grade != null) sj.add("grade=" + grade);
        if (status != null) sj.add("status=" + status);

        // 정렬 조건 필드 검증 (List 포맷팅 적용)
        if (sort != null && !sort.isEmpty()) {
            sj.add("sort=[" + String.join(", ", sort) + "]");
        }

        return sj.toString();
    }
}
