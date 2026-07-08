package dev.bum.common.service.ticket.seat.vo;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertSeatAreaConfig {
    @NotNull
    private SeatGrade grade;
    @NotBlank
    private String zone;
    @NotNull
    private Integer rows;
    @NotNull
    private Integer cols;
    @NotNull
    private Integer price;

    private Integer startRow;
    private Integer startCol;
    private Double startX;
    private Double startY;
    private Double seatWidth;
    private Double seatHeight;
    private Double gapX;
    private Double gapY;
    private Double rotation;
    private Double layoutAngle;
}
