package dev.bum.ticket_service.vo.seat;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertSeatInfo {

    @NotNull
    private Long eventId;

    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
