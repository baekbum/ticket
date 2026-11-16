package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.paymentgateway.card.GatewayCardSessionResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Service
@Transactional
public class GatewayCardCheckoutCommands {

    // 입금·취소·결과 통보와 동일한 예매 → 결제 순서로 잠근다.
    private final VirtualAccountPaymentLockService locks;

    private final long merchantId;

    public GatewayCardCheckoutCommands(
            VirtualAccountPaymentLockService locks,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드가 필요합니다.");
        }

        this.locks = locks;
        this.merchantId = merchantId;
    }

    // 세션 발급에 사용할 기존 결제를 조회한다. PG 준비 요청을 만들거나 실행하지 않는다.
    public PaymentResponse findPayment(String userId, String paymentNo) {

        return owned(userId, paymentNo).toResponse();
    }

    public GatewayCardCheckoutPreparation findPreparation(String userId, String paymentNo) {

        Payment payment = owned(userId, paymentNo);

        if (payment.getGatewayExpiresAt() == null) {
            return new GatewayCardCheckoutPreparation(null, null, payment.toResponse());
        }

        GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(merchantId, paymentNo, userId,
                PaymentMethod.CREDIT_CARD, BigDecimal.valueOf(payment.getAmount()), payment.getGatewayExpiresAt(), null);

        return new GatewayCardCheckoutPreparation(request, payment.getPgPaymentId(), payment.toResponse());
    }

    // 최초 PG 응답의 조건을 확인한 뒤 ID만 별도 커밋한다. 금융 HTTP는 잠금 밖에서 실행한다.
    public PaymentResponse prepared(
            String userId,
            String paymentNo,
            GatewayPaymentPrepareResponse response
    ) {

        Payment payment = owned(userId, paymentNo);

        if (response == null || !Long.valueOf(merchantId).equals(response.merchantId())
                || !paymentNo.equals(response.paymentNo()) || !userId.equals(response.buyerReference())
                || response.paymentMethod() != PaymentMethod.CREDIT_CARD || response.bankCompany() != null
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0
                || !Objects.equals(payment.getGatewayExpiresAt(), response.expiresAt()) || response.status() == null) {
            throw new IllegalStateException("PG 카드 준비 결과가 최초 결제 조건과 일치하지 않습니다.");
        }

        payment.bindGatewayPayment(response.pgPaymentId());

        return payment.toResponse();
    }

    // HTTP 응답 수신 후에도 현재 연결된 원거래와 소유권을 다시 대조한다.
    public void validateSession(String userId, String paymentNo, GatewayCardSessionResponse session) {

        Payment payment = owned(userId, paymentNo);

        if (session == null || payment.getGatewayExpiresAt() == null || payment.getPgPaymentId() == null
                || !Long.valueOf(merchantId).equals(session.merchantId())
                || !payment.getPgPaymentId().equals(session.pgPaymentId())
                || !paymentNo.equals(session.paymentNo()) || !userId.equals(session.buyerReference())
                || session.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(session.amount()) != 0
                || session.paymentSession() == null || session.paymentSession().isBlank()
                || session.expiresAt() == null || !session.expiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("PG 카드 결제 세션을 확인할 수 없습니다.");
        }
    }

    private Payment owned(String userId, String paymentNo) {

        Payment payment = locks.lock(paymentNo);

        if (userId == null || !userId.equals(payment.getReservation().getUserId())) {
            throw new AccessDeniedException("본인 카드 결제만 처리할 수 있습니다.");
        }

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new dev.bum.ticket_service.exception.payment.GatewayPaymentMethodChangedException();
        }

        return payment;
    }
}
