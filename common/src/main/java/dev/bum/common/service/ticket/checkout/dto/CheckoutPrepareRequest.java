package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutPrepareRequest {

    @NotBlank
    private String orderId;

    @NotNull
    private Long eventId;

    @NotNull
    private List<SeatInfo> seats;
}
