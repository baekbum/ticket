package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentValidationRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class CardPaymentService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final PaymentExpirationService paymentExpirationService;

    /**
     * payment가 결제 가능한 상태인지 체크하는 로직
     * @param request
     * @return
     */
    public PaymentResponse validateBeforeApproval(CardPaymentValidationRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.paymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateOwnerAndMethod(payment, request.userId());

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("승인 가능한 결제 상태가 아닙니다.");
        }
        if (isExpired(payment)) {
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }
        return payment.toResponse();
    }

    public CardPaymentSettlementResponse settleFromGateway(CardPaymentCompleteRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        validateOwnerAndMethod(payment, request.getUserId());

        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
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

    private void validateOwnerAndMethod(Payment payment, String userId) {
        if (payment.getReservation() == null || !java.util.Objects.equals(userId, payment.getReservation().getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 요청입니다.");
        }
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }
    }

    private boolean isExpired(Payment payment) {
        return payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private void validateTransaction(Payment payment, CardPaymentCompleteRequest request) {
        if (payment.getCardInfo() == null
                || !request.getTransactionId().equals(payment.getCardInfo().getTransactionId())) {
            throw new IllegalArgumentException("기존 카드 승인 거래번호와 일치하지 않습니다.");
        }
    }

    public PaymentResponse completeFromGateway(CardPaymentCompleteRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validateGatewayCardCompletion(payment, reservation, request);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        return paymentCompletionService.completeCard(
                payment,
                request.getTransactionId(),
                request.getCardCompany(),
                request.getMaskedCardNumber(),
                LocalDateTime.now()
        );
    }

    public PaymentResponse failFromGateway(CardPaymentFailRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validateGatewayCardFailure(payment, reservation, request);
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return payment.toResponse();
        }

        payment.fail();
        return payment.toResponse();
    }

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

    private void validateGatewayCardFailure(
            Payment payment,
            Reservation reservation,
            CardPaymentFailRequest request
    ) {
        if (reservation == null || !request.getUserId().equals(reservation.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 실패 요청입니다.");
        }
        if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
            throw new IllegalArgumentException("카드 결제 요청이 아닙니다.");
        }
        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalArgumentException("카드 결제 실패 처리할 수 없는 상태입니다.");
        }
    }

    private void validatePaymentNotExpired(Payment payment) {
        if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
            paymentExpirationService.expire(payment);
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }
    }
}
