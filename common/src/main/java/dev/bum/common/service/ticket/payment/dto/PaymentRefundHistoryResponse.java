package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRefundHistoryResponse {

    private Long paymentRefundHistoryId;
    private Long paymentId;
    private Long reservationId;
    private String paymentNo;
    private PaymentMethod method;
    private Integer refundAmount;
    private Integer refundedAmountAfter;
    private Integer refundableAmountAfter;
    private PaymentStatus paymentStatusAfter;
    private boolean fullCancellation;

    @Builder.Default
    private List<PaymentRefundHistoryTicketResponse> tickets = new ArrayList<>();

    private String createdAt;
}
