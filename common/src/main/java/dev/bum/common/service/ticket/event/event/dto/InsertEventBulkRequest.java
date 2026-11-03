package dev.bum.common.service.ticket.event.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class InsertEventBulkRequest {

    // 공통 처리 정보.
    @Valid
    @NotNull
    private InsertEventCommonRequest common;

    // 공연 회차 목록.
    @Valid
    @NotEmpty
    private List<InsertEventScheduleRequest> schedules;
}
