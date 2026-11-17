package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.paymentgateway.payment.*;
import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.exception.payment.GatewayMethodChangeUnconfirmedException;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.service.checkout.CheckoutTransactionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class BankToCardCheckoutService {

    private final CheckoutTransactionService checkouts;

    private final VirtualAccountIssuanceCheckoutCommandService accounts;

    private final CheckoutMethodChangeCommandService commands;

    private final PaymentGatewayTransactionClient gateway;

    public PaymentResponse change(String userId, String activeToken, CheckoutConfirmRequest request) {

        CheckoutTransactionService.BankToCardPreparation preparation = checkouts.prepareBankToCardChange(userId, activeToken, request);

        if (preparation.request() == null) {
            return replay(preparation.payment());
        }

        GatewayBankMethodChangeResponse response;

        try {
            PaymentResponse payment = prepareGatewayPayment(userId, preparation.payment());

            if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                return replay(payment);
            }

            response = requestMethodChange(payment.getPgPaymentId(), preparation.request());
        } catch (FeignException failure) {
            if (failure.status() < 0 || failure.status() >= 500) {
                throw new GatewayMethodChangeUnconfirmedException(failure);
            }

            throw failure;
        }

        return completeMethodChange(userId, preparation.payment().getPaymentNo(), preparation.request(), response);
    }

    // 최초 은행 준비 조건을 유지하며, 이미 연결된 거래는 다시 prepare하지 않는다.
    private PaymentResponse prepareGatewayPayment(String userId, PaymentResponse payment) {

        if (payment.getPgPaymentId() != null) {
            return payment;
        }

        try {
            VirtualAccountIssuePreparation original = accounts.findPreparation(payment.getPaymentNo());

            if (original.request() == null) {
                throw new IllegalStateException("최초 은행 준비 조건을 찾을 수 없습니다.");
            }

            accounts.prepared(payment.getPaymentNo(), gateway.prepare(original.request()));

            return commands.findPayment(userId, payment.getPaymentNo());
        } catch (RuntimeException failure) {
            PaymentResponse current = commands.findPayment(userId, payment.getPaymentNo());

            if (current.getMethod() == PaymentMethod.CREDIT_CARD) {
                return current;
            }

            throw failure;
        }
    }

    private GatewayBankMethodChangeResponse requestMethodChange(String pgPaymentId, GatewayBankMethodChangeRequest request) {

        return gateway.changeBankToCard(pgPaymentId, request);
    }

    private PaymentResponse completeMethodChange(String userId, String paymentNo,
            GatewayBankMethodChangeRequest request, GatewayBankMethodChangeResponse response) {

        return commands.confirmBankToCard(userId, paymentNo, request, response);
    }

    private PaymentResponse replay(PaymentResponse payment) {

        return payment;
    }
}
