package dev.bum.ticket_service.service.payment.card;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.payment.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.payment.payment.PaymentExpirationService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class CardPaymentService {

    // 신규 PG 통보는 예매 → 결제 순서를 유지한다.
    private final VirtualAccountPaymentLockService gatewayLocks;

    // 결제 완료 업무 처리를 담당하는 서비스.
    private final PaymentCompletionService paymentCompletionService;
    // 결제 만료 업무 처리를 담당하는 서비스.
    private final PaymentExpirationService paymentExpirationService;

    // 표준 PG 통보는 결제번호뿐 아니라 저장된 PG 거래 ID까지 대조한다.
    public CardPaymentSettlementResponse settleFromGateway(CardPaymentCompleteRequest request, String pgPaymentId) {

        if (pgPaymentId == null || pgPaymentId.isBlank()) {
            throw new IllegalArgumentException("PG 카드 거래 식별자가 필요합니다.");
        }

        return settle(request, pgPaymentId);
    }

    private CardPaymentSettlementResponse settle(CardPaymentCompleteRequest request, String pgPaymentId) {

        Payment payment = gatewayLocks.lock(request.getPaymentNo());

        validateOwner(payment, request.getUserId());

        if (!pgPaymentId.equals(payment.getPgPaymentId())) {
            throw new IllegalArgumentException("카드 통보의 PG 원거래가 일치하지 않습니다.");
        }

        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.REJECTED, payment.toResponse());
        }

        // 이미 결제를 완료한 건이므로 기존 결제 정보를 반환한다.
        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            validateTransaction(payment, request);

            return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.COMPLETED, payment.toResponse());
        }

        // 만료 시간이 지난 경우, 승인 거절 응답을 반환한다.
        if (payment.getStatus() == PaymentStatus.READY && isExpired(payment)) {

            // gateway가 승인을 취소하기 전에 만료 처리와 예매 정리가 커밋되도록 예외 없이 반환한다.
            paymentExpirationService.expire(payment);
            return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.REJECTED, payment.toResponse());
        }

        // 상태가 이미 만료, 취소, 실패 시 승인 거절 응답을 반환한다.
        if (payment.getStatus() == PaymentStatus.EXPIRED
                || payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.FAILED) {

            return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.REJECTED, payment.toResponse());
        }

        validateGatewayCardCompletion(payment, payment.getReservation(), request);
        PaymentResponse completed = paymentCompletionService.completeCard(
                payment, request.getTransactionId(),
                request.getCardCompany(),
                request.getMaskedCardNumber(),
                LocalDateTime.now()
        );

        return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.COMPLETED, completed);
    }

    /**
     * 요청자가 처리 대상의 소유자인지 검증한다.
     */
    private void validateOwner(Payment payment, String userId) {

        if (payment.getReservation() == null || !Objects.equals(userId, payment.getReservation().getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 요청입니다.");
        }
    }

    /**
     * 유효 기간이 만료되었는지 확인한다.
     */
    private boolean isExpired(Payment payment) {

        return payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /**
     * 결제 거래 식별자와 거래 상태를 검증한다.
     */
    private void validateTransaction(Payment payment, CardPaymentCompleteRequest request) {

        if (payment.getCardInfo() == null
                || !request.getTransactionId().equals(payment.getCardInfo().getTransactionId())) {
            throw new IllegalArgumentException("기존 카드 승인 거래번호와 일치하지 않습니다.");
        }
    }

    /**
     * 게이트웨이의 카드 결제 완료 통지를 검증한다.
     */
    private void validateGatewayCardCompletion(
            Payment payment,
            Reservation reservation,
            CardPaymentCompleteRequest request
    ) {

        if (reservation == null || !request.getUserId().equals(reservation.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 완료 요청입니다.");
        }

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }

        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            validateTransaction(payment, request);
            return;
        }

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("카드 결제 완료 처리할 수 없는 상태입니다.");
        }

        validatePaymentNotExpired(payment);
    }

    /**
     * 결제 요청의 유효 기간이 지나지 않았는지 검증한다.
     */
    private void validatePaymentNotExpired(Payment payment) {

        if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
            paymentExpirationService.expire(payment);
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }
    }
}
