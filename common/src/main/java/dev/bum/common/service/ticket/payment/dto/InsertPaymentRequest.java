package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertPaymentRequest {

    @NotNull
    private Long reservationId;

    @NotNull
    private PaymentMethod method;

    @NotNull
    private Integer amount;

    private String idempotencyKey;

    private String depositorName;
}
