package dev.bum.common.service.paymentgateway.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// 발급된 거래는 성공 응답으로 수단을 바꾸지 않고 409로 거절한다.
public record GatewayBankMethodChangeResponse(String pgPaymentId, Long merchantId, String paymentNo,
                                             String buyerReference, PaymentMethod paymentMethod,
                                             BigDecimal amount, LocalDateTime expiresAt, int methodRevision) { }
