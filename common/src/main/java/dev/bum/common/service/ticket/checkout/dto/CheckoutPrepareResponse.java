package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutPrepareResponse {

    private Long reservationId;
    private String orderId;
    private Long paymentId;
    private String paymentNo;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private Integer totalTicketAmount;
    private Integer discountAmount;
    private Integer amount;
}
