package dev.bum.common.service.ticket.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatRedisInspectResponse {

    // 적용 범위.
    private String scope;

    // 공연 식별자.
    private Long eventId;

    // 좌석 구역 식별자.
    private Long areaId;

    // 좌석 식별자.
    private Long seatId;

    // 최대 조회 건수.
    private Integer limit;

    // 조회 또는 처리 건수.
    private Integer count;

    // 조회한 항목 목록.
    private List<SeatRedisEntryResponse> entries;
}
