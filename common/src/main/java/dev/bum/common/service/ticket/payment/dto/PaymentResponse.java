package dev.bum.common.service.ticket.payment.dto;

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
public class PaymentResponse {

    private Long paymentId;
    private Long reservationId;
    private String orderId;
    private String paymentNo;
    private PaymentMethod method;
    private PaymentStatus status;
    private Integer amount;
    private String bankName;
    private String accountNumber;
    private String depositorName;
    private String requestedAt;
    private String paidAt;
    private String expiresAt;
}
