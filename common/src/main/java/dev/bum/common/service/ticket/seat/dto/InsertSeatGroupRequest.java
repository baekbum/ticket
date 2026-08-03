package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
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

    @NotBlank
    private String eventGroupCode;

    @NotBlank
    private String areaLayoutKey;

    @NotNull
    private List<InsertSeatAreaConfig> insertSeatAreaConfigs;
}
