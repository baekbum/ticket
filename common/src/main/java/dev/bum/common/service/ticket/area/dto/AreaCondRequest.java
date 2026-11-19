package dev.bum.common.service.ticket.area.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AreaCondRequest {

    // 좌석 구역 식별자.
    private Long areaId;

    // 공연 식별자.
    private Long eventId;

    // 좌석 구역 이름.
    private String areaName;

    // 좌석 배치도 저장 키.
    private String layoutKey;

    // 좌석 등급.
    private SeatGrade grade;

    // 좌석 구역 상태.
    private AreaStatus status;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        StringJoiner sj = new StringJoiner(", ", "AreaCond{", "}");
        sj.add("page=" + page);
        sj.add("size=" + size);
        if (areaId != null) sj.add("areaId=" + areaId);

        if (eventId != null) sj.add("eventId=" + eventId);

        if (areaName != null) sj.add("areaName='" + areaName + "'");

        if (layoutKey != null) sj.add("layoutKey='" + layoutKey + "'");

        if (grade != null) sj.add("grade=" + grade);

        if (status != null) sj.add("status=" + status);

        if (sort != null && !sort.isEmpty()) sj.add("sort=[" + String.join(", ", sort) + "]");
        return sj.toString();
    }
}
