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

    @NotNull
    private Long eventId;

    private Long areaId;

    private SeatInsertMode mode;

    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
