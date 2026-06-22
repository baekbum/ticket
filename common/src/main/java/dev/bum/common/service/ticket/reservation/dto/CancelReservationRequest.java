package dev.bum.common.service.ticket.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class CancelReservationRequest {

    @NotBlank
    private String userId;

    @NotNull
    private List<Long> selectedTicketIdList;

    @NotNull
    private Long eventId;
}
