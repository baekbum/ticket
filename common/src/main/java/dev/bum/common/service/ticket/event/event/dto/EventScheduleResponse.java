package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventScheduleResponse {

    private Long eventId;
    private String eventDateTime;
    private Integer availableSeats;
    private EventStatus status;
    private List<EventSeatPriceResponse> seatPrices;
}
