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
    private Long areaId;
    private Long eventId;
    private String areaName;
    private SeatGrade grade;
    private AreaStatus status;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private List<String> sort;

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "AreaCond{", "}");
        sj.add("page=" + page);
        sj.add("size=" + size);
        if (areaId != null) sj.add("areaId=" + areaId);
        if (eventId != null) sj.add("eventId=" + eventId);
        if (areaName != null) sj.add("areaName='" + areaName + "'");
        if (grade != null) sj.add("grade=" + grade);
        if (status != null) sj.add("status=" + status);
        if (sort != null && !sort.isEmpty()) sj.add("sort=[" + String.join(", ", sort) + "]");
        return sj.toString();
    }
}
