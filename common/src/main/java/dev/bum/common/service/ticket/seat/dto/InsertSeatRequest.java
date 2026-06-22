package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertSeatRequest {

    @NotNull
    private Long eventId;

    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
