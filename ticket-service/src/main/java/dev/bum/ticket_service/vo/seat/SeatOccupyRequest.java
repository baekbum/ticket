package dev.bum.ticket_service.vo.seat;

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
}