package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.payment.CardPaymentService;
import dev.bum.ticket_service.service.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.payment.PaymentExpirationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CardPaymentServiceTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @Mock
    private PaymentExpirationService paymentExpirationService;

    @InjectMocks
    private CardPaymentService cardPaymentService;

    @Test
    @DisplayName("payment-gateway 결제 완료 요청 시 카드 결제를 완료 처리한다")
    void complete_card_payment_from_gateway_success() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentCompleteRequest request = cardCompleteRequest(BigDecimal.valueOf(180000));
        PaymentResponse paymentResponse = paidResponse(PaymentMethod.CREDIT_CARD);

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));
        given(paymentCompletionService.completeCard(
                org.mockito.ArgumentMatchers.eq(payment),
                org.mockito.ArgumentMatchers.eq(request.getTransactionId()),
                org.mockito.ArgumentMatchers.eq(request.getCardCompany()),
                org.mockito.ArgumentMatchers.eq(request.getMaskedCardNumber()),
                org.mockito.ArgumentMatchers.any()
        ))
                .willReturn(paymentResponse);

        PaymentResponse response = cardPaymentService.completeFromGateway(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        then(paymentCompletionService).should().completeCard(
                org.mockito.ArgumentMatchers.eq(payment),
                org.mockito.ArgumentMatchers.eq(request.getTransactionId()),
                org.mockito.ArgumentMatchers.eq(request.getCardCompany()),
                org.mockito.ArgumentMatchers.eq(request.getMaskedCardNumber()),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("payment-gateway 결제 완료 금액이 다르면 결제 상태를 유지한다")
    void complete_card_payment_from_gateway_amount_mismatch() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentCompleteRequest request = cardCompleteRequest(BigDecimal.valueOf(170000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> cardPaymentService.completeFromGateway(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 금액이 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 카드 결제 완료 요청은 공통 만료 처리로 예매와 좌석까지 정리한다")
    void complete_card_payment_from_gateway_expired_payment() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(
                reservation,
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.READY,
                LocalDateTime.now().minusMinutes(1)
        );
        CardPaymentCompleteRequest request = cardCompleteRequest(BigDecimal.valueOf(180000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> cardPaymentService.completeFromGateway(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 기한이 만료되었습니다.");

        then(paymentExpirationService).should().expire(payment);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("payment-gateway 카드 결제 실패 요청 시 READY 결제를 FAILED 처리한다")
    void fail_card_payment_from_gateway_success() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentFailRequest request = cardFailRequest(BigDecimal.valueOf(180000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        PaymentResponse response = cardPaymentService.failFromGateway(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("payment-gateway 카드 결제 실패 요청 금액이 다르면 결제 상태를 유지한다")
    void fail_card_payment_from_gateway_amount_mismatch() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        CardPaymentFailRequest request = cardFailRequest(BigDecimal.valueOf(170000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> cardPaymentService.failFromGateway(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 금액이 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    private CardPaymentCompleteRequest cardCompleteRequest(BigDecimal amount) {
        return CardPaymentCompleteRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .userId("user01")
                .amount(amount)
                .transactionId("CARD-transaction-1")
                .cardCompany(CardCompany.SHINHAN)
                .maskedCardNumber("4111-****-****-1111")
                .build();
    }

    private CardPaymentFailRequest cardFailRequest(BigDecimal amount) {
        return CardPaymentFailRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .userId("user01")
                .amount(amount)
                .failureReason("카드 승인 실패")
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(100)
                .availableSeats(100)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private Reservation reservation(Event event, String userId) {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId(userId)
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .tickets(new ArrayList<>())
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment payment(Reservation reservation, PaymentMethod method, PaymentStatus status) {
        return payment(reservation, method, status, null);
    }

    private Payment payment(Reservation reservation, PaymentMethod method, PaymentStatus status, LocalDateTime expiresAt) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(method)
                .status(status)
                .amount(180000)
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .expiresAt(expiresAt)
                .build();
    }

    private PaymentResponse paidResponse(PaymentMethod paymentMethod) {
        return PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(paymentMethod)
                .status(PaymentStatus.PAID)
                .amount(180000)
                .build();
    }
}
