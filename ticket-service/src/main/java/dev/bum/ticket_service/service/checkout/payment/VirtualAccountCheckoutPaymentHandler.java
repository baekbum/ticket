package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountIssueRequest;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountIssueResponse;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VirtualAccountCheckoutPaymentHandler implements CheckoutPaymentHandler {

    private final PaymentGatewayVirtualAccountClient paymentGatewayVirtualAccountClient;

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public void process(CheckoutConfirmRequest request, Payment payment) {
        GatewayVirtualAccountIssueResponse virtualAccount = issueVirtualAccountFromGateway(request, payment);
        payment.waitDeposit(
                virtualAccount.getBankName(),
                virtualAccount.getAccountNumber(),
                virtualAccount.getExpiresAt()
        );
    }

    private GatewayVirtualAccountIssueResponse issueVirtualAccountFromGateway(
            CheckoutConfirmRequest request,
            Payment payment
    ) {
        BankCompany bankCompany = resolveBankCompany(request);
        return paymentGatewayVirtualAccountClient.issue(
                GatewayVirtualAccountIssueRequest.builder()
                        .paymentNo(payment.getPaymentNo())
                        .bankCompany(bankCompany)
                        .amount(BigDecimal.valueOf(payment.getAmount()))
                        .eventDateTime(payment.getReservation().getEvent().getEventDateTime())
                        .ticketPaymentApplyRequired(false)
                        .build()
        );
    }

    private BankCompany resolveBankCompany(CheckoutConfirmRequest request) {
        if (!StringUtils.hasText(request.getBankCode())) {
            throw new IllegalArgumentException("은행 코드가 필요합니다.");
        }
        try {
            return BankCompany.valueOf(request.getBankCode().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 은행 코드입니다.");
        }
    }
}
