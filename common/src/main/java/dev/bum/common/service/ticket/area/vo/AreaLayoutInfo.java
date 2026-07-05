package dev.bum.common.service.ticket.area.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AreaLayoutInfo {
    private Double positionX;
    private Double positionY;
    private Double width;
    private Double height;
    private Double rotation;
    private Double layoutAngle;
    private String svgPath;
    private String areaColor;
}
