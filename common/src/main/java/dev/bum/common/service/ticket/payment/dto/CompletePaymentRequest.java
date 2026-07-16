package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompletePaymentRequest {

    @NotBlank
    private String paymentNo;

    private LocalDateTime paidAt;
}
