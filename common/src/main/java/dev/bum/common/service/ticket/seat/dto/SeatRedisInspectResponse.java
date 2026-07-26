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
    private String scope;
    private Long eventId;
    private Long areaId;
    private Long seatId;
    private Integer limit;
    private Integer count;
    private List<SeatRedisEntryResponse> entries;
}
