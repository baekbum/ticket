package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRefundProcessService {

    private final PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Payment payment,
            List<Ticket> selectedTickets,
            int refundAmount,
            boolean fullCancellation,
            RefundAccountRequest refundAccount
    ) {
        PaymentRefundProcess process = paymentRefundProcessJpaRepository.save(
                PaymentRefundProcess.create(payment, selectedTickets, refundAmount, fullCancellation, refundAccount)
        );
        return process.getPaymentRefundProcessId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewaySucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::gatewaySucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGatewayFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.gatewayFailed(messageOf(throwable)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalSucceeded(Long paymentRefundProcessId) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(PaymentRefundProcess::localSucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLocalFailed(Long paymentRefundProcessId, Throwable throwable) {
        if (paymentRefundProcessId == null) {
            return;
        }
        paymentRefundProcessJpaRepository.findById(paymentRefundProcessId)
                .ifPresent(process -> process.localFailed(messageOf(throwable)));
    }

    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }
}
