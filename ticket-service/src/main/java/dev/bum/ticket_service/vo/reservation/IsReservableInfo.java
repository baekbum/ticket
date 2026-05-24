package dev.bum.ticket_service.vo.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class IsReservableInfo {
    @NotBlank
    private String userId;
    @NotNull
    private long eventId;
    @NotNull
    private int selectedSeatCnt;
}
