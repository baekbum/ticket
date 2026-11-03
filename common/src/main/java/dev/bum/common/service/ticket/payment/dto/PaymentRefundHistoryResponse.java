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

    // 결제 환불 이력 식별자.
    private Long paymentRefundHistoryId;

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

    // 처리 후 누적 환불 금액.
    private Integer refundedAmountAfter;

    // 처리 후 환불 가능 잔액.
    private Integer refundableAmountAfter;

    // 처리 후 결제 상태.
    private PaymentStatus paymentStatusAfter;

    // 전체 취소 여부.
    private boolean fullCancellation;

    // 티켓 목록.
    @Builder.Default
    private List<PaymentRefundSnapshotTicketResponse> tickets = new ArrayList<>();

    // 생성 시각.
    private String createdAt;
}
