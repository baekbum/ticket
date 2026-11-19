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

    // 티켓 한 장당 예매 수수료.
    private Integer reservationFeePerTicket;

    // 배송비.
    private Integer deliveryFee;
}
