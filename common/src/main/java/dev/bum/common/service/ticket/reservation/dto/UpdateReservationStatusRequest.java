package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReservationStatusRequest {

    // 예매 상태.
    @NotNull
    private ReservationStatus status;

    // 선택한 티켓 식별자 목록.
    @Builder.Default
    private List<Long> selectedTicketIdList = new ArrayList<>();

    // 처리 사유.
    @NotBlank
    private String reason;
}
