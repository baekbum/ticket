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
public class CardPaymentApproveRequest {

    @NotBlank
    private String paymentNo;

    @NotBlank
    private String cardCompany;

    @NotBlank
    private String cardNumber;

    @NotBlank
    private String cvc;

    @NotBlank
    private String cardPassword;
}
