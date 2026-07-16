package dev.bum.common.service.ticket.event.eventLayout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventLayoutResponse {
    private Long layoutId;
    private Long eventId;
    private String originalFileName;
    private String svgText;
}
