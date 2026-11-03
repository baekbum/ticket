package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.enums.SeatInsertMode;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertSeatRequest {

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 좌석 구역 식별자.
    private Long areaId;

    // 조회 또는 처리 모드.
    private SeatInsertMode mode;

    // 좌석 생성에 사용할 구역별 설정.
    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
