package dev.bum.common.service.ticket.event.event.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteEventBulkRequest {
    @NotEmpty
    private List<Long> eventIds;
}
