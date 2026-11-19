package dev.bum.ticket_service.exception.reservation;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import lombok.Getter;

@Getter
public class GatewayRefundRequestException extends RuntimeException {

    private final String code;
    private final int status;
    private final PaymentRefundProcessResponse application;

    public GatewayRefundRequestException(String code, int status, String message, PaymentRefundProcessResponse application) {

        super(message);
        this.code = code;
        this.status = status;
        this.application = application;
    }
}
