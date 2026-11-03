package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
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

    // 결제 식별자.
    private Long paymentId;

    // 예매 식별자.
    private Long reservationId;

    // 주문 식별자.
    private String orderId;

    // 결제 번호.
    private String paymentNo;

    // 결제 수단.
    private PaymentMethod method;

    // 결제 상태.
    private PaymentStatus status;

    // 금액.
    private Integer amount;

    // 누적 환불 금액.
    private Integer refundedAmount;

    // 환불 가능 잔액.
    private Integer refundableAmount;

    // 카드 승인 거래 식별자.
    private String cardTransactionId;

    // 카드사.
    private CardCompany cardCompany;

    // 마스킹한 카드 번호.
    private String maskedCardNumber;

    // 은행 이름.
    private String bankName;

    // 계좌 번호.
    private String accountNumber;

    // 입금자 이름.
    private String depositorName;

    // 요청 시각.
    private String requestedAt;

    // 결제 완료 시각.
    private String paidAt;

    // 만료 시각.
    private String expiresAt;
}
