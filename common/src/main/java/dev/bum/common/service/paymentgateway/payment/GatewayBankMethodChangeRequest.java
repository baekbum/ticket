package dev.bum.common.service.paymentgateway.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayBankMethodChangeRequest(Long merchantId, @NotBlank String buyerReference,
                                            @NotNull @Positive BigDecimal amount,
                                            @NotNull LocalDateTime expiresAt) { }
