package dev.bum.common.service.ticket.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRefundSnapshotTicketResponse {

    // 환불 시점 티켓 스냅샷 식별자.
    private Long paymentRefundSnapshotTicketId;

    // 티켓 식별자.
    private Long ticketId;

    // 티켓 가격.
    private Integer ticketPrice;
}
