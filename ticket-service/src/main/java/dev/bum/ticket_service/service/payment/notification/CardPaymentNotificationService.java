package dev.bum.ticket_service.service.payment.notification;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotificationResponse;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.service.payment.payment.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CardPaymentNotificationService {

    // 기존 소유자 검증·결제 완료·취소/만료·멱등 처리 경로를 재사용한다.
    private final PaymentService payments;
    // PG가 Ticksy에 발급한 공개 가맹점 코드. 다른 가맹점의 통보를 수신하지 않는다.
    private final long merchantId;

    /**
     * 가맹점 서버의 등록 코드와 기존 결제 처리 서비스를 주입한다.
     */
    public CardPaymentNotificationService(
            PaymentService payments,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드 설정이 필요합니다.");
        }

        this.payments = payments;
        this.merchantId = merchantId;
    }

    /**
     * PG 표준 통보를 수신하고 로컬 결제 반영 결과를 공통 업무 응답으로 변환한다.
     */
    @Transactional
    public CardPaymentNotificationResponse receive(CardPaymentNotification notification) {

        notification.validateContract();

        if (!Long.valueOf(merchantId).equals(notification.merchantId())) {
            throw new AccessDeniedException("이 가맹점에 대한 PG 통보가 아닙니다.");
        }

        CardPaymentCompleteRequest request = CardPaymentCompleteRequest.builder()
                .paymentNo(notification.merchantPaymentNo()).userId(notification.buyerReference())
                .amount(notification.amount()).transactionId(notification.transactionId())
                .cardCompany(notification.cardCompany()).maskedCardNumber(notification.maskedCardNumber()).build();
        CardPaymentSettlementResponse result = payments.settleCardFromGateway(request, notification.pgPaymentId());
        CardPaymentNotificationResponse.Outcome outcome = outcome(notification, result);

        return CardPaymentNotificationResponse.of(notification, outcome);
    }

    /**
     * Ticksy 내부 상태는 여기서 해석하고 PG에는 공통 수락/확정 거절만 반환한다.
     */
    private CardPaymentNotificationResponse.Outcome outcome(
            CardPaymentNotification notification,
            CardPaymentSettlementResponse result
    ) {

        PaymentResponse payment = result == null ? null : result.payment();

        if (payment == null || !notification.merchantPaymentNo().equals(payment.getPaymentNo())
                || payment.getAmount() == null
                || notification.amount().compareTo(BigDecimal.valueOf(payment.getAmount())) != 0) {
            throw new IllegalStateException("PG 통보의 로컬 결제 반영 결과를 확인할 수 없습니다.");
        }

        if (result.outcome() == CardPaymentSettlementResponse.Outcome.COMPLETED
                && payment.getMethod() == PaymentMethod.CREDIT_CARD
                && notification.transactionId().equals(payment.getCardTransactionId())
                && (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.REFUNDED
                    || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED)) {
            return CardPaymentNotificationResponse.Outcome.ACCEPTED;
        }

        if (result.outcome() == CardPaymentSettlementResponse.Outcome.REJECTED
                && (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.EXPIRED
                    || payment.getStatus() == PaymentStatus.FAILED || payment.getMethod() == PaymentMethod.BANK_TRANSFER)) {
            return CardPaymentNotificationResponse.Outcome.REJECTED;
        }

        throw new IllegalStateException("PG 통보에 대한 업무 결과를 확정할 수 없습니다.");
    }
}
