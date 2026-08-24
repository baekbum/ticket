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

    private Long paymentRefundProcessId;
    private Long reservationId;
    private Long paymentId;
    private String paymentNo;
    private PaymentMethod method;
    private String status;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private List<String> sort;
}
