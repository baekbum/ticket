package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.paymentgateway.refund.GatewayPaymentRefundRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;

import java.math.BigDecimal;

final class RefundGatewayRequestFactory {
    private RefundGatewayRequestFactory() {}

    static GatewayPaymentRefundRequest from(PaymentRefundProcess process, long merchantId, RefundAccountCipher cipher) {
        var snapshot = process.getRefund().getSnapshot();
        return new GatewayPaymentRefundRequest(merchantId, process.getReservation().getUserId(),
                BigDecimal.valueOf(snapshot.getRefundAmount()), snapshot.getRefundBankCompany(),
                process.getPayment().getMethod() == PaymentMethod.BANK_TRANSFER
                        ? cipher.decrypt(snapshot.getRefundAccountCiphertext()) : null, snapshot.getRefundAccountHolder());
    }
}
