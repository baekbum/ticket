package dev.bum.common.service.ticket.area.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAreaRequest {
    private String areaName;
    private SeatGrade grade;
    private Integer price;
    private Double positionX;
    private Double positionY;
    private Double width;
    private Double height;
    private Double rotation;
    private Double layoutAngle;
    private AreaStatus status;
}
