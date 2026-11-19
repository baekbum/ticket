package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.enums.SeatInsertMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertSeatGroupRequest {

    // 같은 공연의 회차를 묶는 그룹 코드.
    @NotBlank
    private String eventGroupCode;

    // 구역 배치도 저장 키.
    @NotBlank
    private String areaLayoutKey;

    // 조회 또는 처리 모드.
    private SeatInsertMode mode;

    // 좌석 생성에 사용할 구역별 설정.
    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
