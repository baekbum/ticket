package dev.bum.common.service.ticket.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VirtualAccountIssueRequest {

    @NotBlank
    private String paymentNo;

    @NotBlank
    private String bankCode;

    @NotBlank
    private String depositorName;
}
