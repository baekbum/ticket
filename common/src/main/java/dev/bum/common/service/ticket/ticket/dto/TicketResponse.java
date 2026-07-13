package dev.bum.common.service.ticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponse {

    private long ticketId;
    private long seatId;
    private String zone;
    private Integer seatRow;
    private Integer seatCol;
    private String seatName;
    private String grade;
    private int price;
    private String status;
}
