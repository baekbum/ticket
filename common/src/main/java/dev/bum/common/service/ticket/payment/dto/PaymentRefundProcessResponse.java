package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundProcessResponse {

    private Long paymentRefundProcessId;
    private Long paymentId;
    private Long reservationId;
    private String paymentNo;
    private PaymentMethod method;
    private Integer refundAmount;
    private boolean fullCancellation;
    private String selectedTicketIds;
    private String status;
    private BankCompany refundBankCompany;
    private String refundAccountNumberMasked;
    private String refundAccountHolder;
    private String failureReason;
    private Integer retryCount;
    private String lastTriedAt;
    private String completedAt;
    private String createdAt;
    private String updatedAt;
}
