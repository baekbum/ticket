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

    // 결제 환불 처리 식별자.
    private Long paymentRefundProcessId;

    // 결제 식별자.
    private Long paymentId;

    // 예매 식별자.
    private Long reservationId;

    // 결제 번호.
    private String paymentNo;

    // 결제 수단.
    private PaymentMethod method;

    // 환불 금액.
    private Integer refundAmount;

    // 전체 취소 여부.
    private boolean fullCancellation;

    // 선택한 티켓 식별자 목록.
    private String selectedTicketIds;

    // 결제 환불 처리 상태.
    private String status;

    // 환불받을 은행.
    private BankCompany refundBankCompany;

    // 마스킹한 환불 계좌 번호.
    private String refundAccountNumberMasked;

    // 환불 계좌 예금주.
    private String refundAccountHolder;

    // 실패 사유.
    private String failureReason;

    // 재시도 횟수.
    private Integer retryCount;

    // 마지막 시도 시각.
    private String lastTriedAt;

    // 처리 완료 시각.
    private String completedAt;

    // 생성 시각.
    private String createdAt;

    // 마지막 수정 시각.
    private String updatedAt;
}
