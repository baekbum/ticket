package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
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

    public PaymentResponse completeFromGateway(CardPaymentCompleteRequest request) {
        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = payment.getReservation();

        validateGatewayCardCompletion(payment, reservation, request);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        return paymentCompletionService.complete(payment, LocalDateTime.now());
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
