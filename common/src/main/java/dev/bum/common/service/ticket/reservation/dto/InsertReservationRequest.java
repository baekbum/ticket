package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertReservationRequest {

    @NotBlank
    private String userId;

    @NotNull
    private List<SeatInfo> seats;

    @NotNull
    private Long eventId;
}
