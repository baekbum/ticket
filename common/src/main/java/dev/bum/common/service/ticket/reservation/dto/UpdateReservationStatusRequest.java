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

    @NotNull
    private ReservationStatus status;

    @Builder.Default
    private List<Long> selectedTicketIdList = new ArrayList<>();

    @NotBlank
    private String reason;
}
