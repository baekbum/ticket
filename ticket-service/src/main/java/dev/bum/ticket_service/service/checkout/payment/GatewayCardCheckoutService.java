package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayTransactionClient;
import dev.bum.ticket_service.exception.payment.GatewayCardPreparationUnconfirmedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardCheckoutService {

    private final GatewayCardCheckoutCommands commands;

    private final PaymentGatewayTransactionClient gateway;

    // 최초 조건으로 PG 준비를 재요청한다. 서버/브라우저 응답 유실에도 새 결제번호를 만들지 않는다.
    public PaymentResponse prepare(String userId, String paymentNo) {

        GatewayCardCheckoutPreparation preparation = commands.findPreparation(userId, paymentNo);

        if (preparation.request() == null || preparation.pgPaymentId() != null) {
            return preparation.payment();
        }

        try {
            return commands.prepared(userId, paymentNo, gateway.prepare(preparation.request()));
        } catch (RuntimeException failure) {
            throw new GatewayCardPreparationUnconfirmedException(failure);
        }
    }

    // 카드 원문을 받지 않는다. ID 저장 커밋 이후에만 거래 전용 세션을 브라우저에 전달한다.
    public GatewayCardSessionResponse session(String userId, String paymentNo) {

        PaymentResponse payment = commands.findPayment(userId, paymentNo);

        if (payment.getPgPaymentId() == null) {
            throw new IllegalArgumentException("PG 거래가 연결되지 않았습니다. 결제 준비를 먼저 완료해주세요.");
        }

        GatewayCardSessionResponse session = gateway.cardSession(payment.getPgPaymentId(),
                new GatewayCardSessionRequest(userId, payment.getStatus() == PaymentStatus.READY));

        commands.validateSession(userId, paymentNo, session);

        return session;
    }
}
