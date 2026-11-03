package dev.bum.common.service.ticket.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReservationDeliveryTrackingRequest {

    // 택배사.
    @NotBlank
    private String carrier;

    // 운송장 번호.
    @NotBlank
    private String trackingNumber;
}
