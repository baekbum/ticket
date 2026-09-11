package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSeatPriceResponse {

    private String areaName;
    private SeatGrade grade;
    private Integer price;
}
