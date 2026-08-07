package dev.bum.common.service.ticket.event.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertEventScheduleRequest {

    @NotNull
    private LocalDateTime eventDateTime;

    @NotNull
    private LocalDateTime saleStartAt;

    @NotNull
    private LocalDateTime saleEndAt;

    @NotNull
    private LocalDateTime cancelDeadlineAt;
}
