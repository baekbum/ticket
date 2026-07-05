package dev.bum.common.service.ticket.area.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertAreaRequest {
    @NotNull
    private Long eventId;

    @NotBlank
    private String areaName;

    @NotNull
    private SeatGrade grade;

    @NotNull
    private Integer price;

    private Double positionX;
    private Double positionY;
    private Double width;
    private Double height;
    private Double rotation;
    private Double layoutAngle;
    private String svgPath;
    private String areaColor;
    private AreaStatus status;
}
