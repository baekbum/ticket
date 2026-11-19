package dev.bum.common.service.ticket.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IsReservableRequest {

    // 사용자 ID.
    @NotBlank
    private String userId;

    // 공연 식별자.
    @NotNull
    private long eventId;

    // 선택한 좌석 수.
    @NotNull
    private int selectedSeatCnt;
}
