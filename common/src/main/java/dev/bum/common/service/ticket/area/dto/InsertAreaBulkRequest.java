package dev.bum.common.service.ticket.area.dto;

import jakarta.validation.Valid;
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
public class InsertAreaBulkRequest {
    @Valid
    @NotEmpty
    private List<InsertAreaRequest> areas;
}
