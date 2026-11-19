package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundProcessCondRequest {

    // 결제 환불 처리 식별자.
    private Long paymentRefundProcessId;

    // 예매 식별자.
    private Long reservationId;

    // 결제 식별자.
    private Long paymentId;

    // 결제 번호.
    private String paymentNo;

    // 결제 수단.
    private PaymentMethod method;

    // 결제 환불 처리 상태.
    private String status;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;
}
