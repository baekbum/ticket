package dev.bum.common.service.ticket.checkout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutFeeResponse {

    private Integer reservationFeePerTicket;
    private Integer deliveryFee;
}
