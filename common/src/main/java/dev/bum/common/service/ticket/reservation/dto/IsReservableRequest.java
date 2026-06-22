package dev.bum.common.service.ticket.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class IsReservableRequest {
    @NotBlank
    private String userId;
    @NotNull
    private long eventId;
    @NotNull
    private int selectedSeatCnt;
}
