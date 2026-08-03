package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatOccupyRequest {

    private Long eventId;
    private String userId;
    private List<SeatInfo> seats;
    private Integer maxTicketsPerPerson;
    private String eventGroupCode;
    private TicketLimitScope ticketLimitScope;
}
