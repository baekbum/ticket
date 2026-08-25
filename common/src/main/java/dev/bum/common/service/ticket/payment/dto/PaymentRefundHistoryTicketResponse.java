package dev.bum.common.service.ticket.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRefundHistoryTicketResponse {

    private Long paymentRefundHistoryTicketId;
    private Long ticketId;
    private Integer ticketPrice;
}
