package dev.bum.ticket_service.vo.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
}
