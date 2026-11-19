package dev.bum.ticket_service.service.payment.card;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentSettlementResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.payment.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.payment.payment.PaymentExpirationService;

import org.springframework.security.access.AccessDeniedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CardPaymentServiceTest {

    private static final String PG_ID = "20261118550e8400-e29b-41d4-a716-446655440000";

    @Mock
    private VirtualAccountPaymentLockService gatewayLocks;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @Mock
    private PaymentExpirationService paymentExpirationService;

    @InjectMocks
    private CardPaymentService cardPaymentService;

    @Test
    void settlementExpiresAndReturnsNormallySoCleanupCanCommit() {
        Payment payment = payment(reservation(event(), "user01"), PaymentMethod.CREDIT_CARD,
                PaymentStatus.READY, LocalDateTime.now().minusMinutes(1));
        payment.bindGatewayPayment(PG_ID);
        given(gatewayLocks.lock(payment.getPaymentNo())).willReturn(payment);
        Mockito.doAnswer(invocation -> { payment.expire(); return null; })
                .when(paymentExpirationService).expire(payment);
        CardPaymentSettlementResponse result = cardPaymentService.settleFromGateway(cardCompleteRequest(BigDecimal.valueOf(180000)), PG_ID);
        assertThat(result.outcome()).isEqualTo(CardPaymentSettlementResponse.Outcome.REJECTED);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    void settlementRetryRequiresSameTransactionEvenAfterExpirationTime() {
        Payment payment = Payment.builder().paymentId(1L).paymentNo("PAY-20260727120000-abcdef123456")
                .reservation(reservation(event(), "user01")).method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID).amount(180000).expiresAt(LocalDateTime.now().minusMinutes(1))
                .cardInfo(CardPaymentInfo.builder().transactionId("CARD-transaction-1").build()).build();
        payment.bindGatewayPayment(PG_ID);
        given(gatewayLocks.lock(payment.getPaymentNo())).willReturn(payment);
        CardPaymentCompleteRequest request = cardCompleteRequest(BigDecimal.valueOf(180000));
        assertThat(cardPaymentService.settleFromGateway(request, PG_ID).outcome())
                .isEqualTo(CardPaymentSettlementResponse.Outcome.COMPLETED);
        request.setTransactionId("different-transaction");
        assertThatThrownBy(() -> cardPaymentService.settleFromGateway(request, PG_ID))
                .hasMessage("기존 카드 승인 거래번호와 일치하지 않습니다.");
        then(paymentCompletionService).shouldHaveNoInteractions();
        then(paymentExpirationService).shouldHaveNoInteractions();
    }

    @Test
    void settlementRejectsTerminalPaymentsWithoutCompletingThem() {
        for (PaymentStatus status : List.of(PaymentStatus.EXPIRED, PaymentStatus.FAILED, PaymentStatus.CANCELLED)) {
            Payment payment = payment(reservation(event(), "user01"), PaymentMethod.CREDIT_CARD, status);
            payment.bindGatewayPayment(PG_ID);
            given(gatewayLocks.lock(payment.getPaymentNo())).willReturn(payment);
            assertThat(cardPaymentService.settleFromGateway(cardCompleteRequest(BigDecimal.valueOf(180000)), PG_ID).outcome())
                    .isEqualTo(CardPaymentSettlementResponse.Outcome.REJECTED);
        }
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    void settlementRejectsApprovalAfterPaymentMethodChangedToBankTransfer() {
        Payment payment = payment(reservation(event(), "user01"), PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        payment.switchToBankTransfer();
        payment.waitDeposit("KB국민은행", "1111-2222-3333-4444", LocalDateTime.now().plusDays(1));
        payment.bindGatewayPayment(PG_ID);
        given(gatewayLocks.lock(payment.getPaymentNo())).willReturn(payment);

        CardPaymentSettlementResponse result = cardPaymentService.settleFromGateway(
                cardCompleteRequest(BigDecimal.valueOf(180000)), PG_ID);

        assertThat(result.outcome()).isEqualTo(CardPaymentSettlementResponse.Outcome.REJECTED);
        assertThat(result.payment().getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    @Test
    void settlementRequiresTheSamePgPaymentOwnerAndAmount() {
        Payment payment = payment(reservation(event(), "user01"), PaymentMethod.CREDIT_CARD, PaymentStatus.READY);
        payment.bindGatewayPayment(PG_ID);
        given(gatewayLocks.lock(payment.getPaymentNo())).willReturn(payment);
        CardPaymentCompleteRequest wrongOwner = cardCompleteRequest(BigDecimal.valueOf(180000));
        wrongOwner.setUserId("another-owner");

        assertThatThrownBy(() -> cardPaymentService.settleFromGateway(wrongOwner, PG_ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> cardPaymentService.settleFromGateway(cardCompleteRequest(BigDecimal.valueOf(170000)), PG_ID))
                .hasMessage("결제 금액이 일치하지 않습니다.");
        assertThatThrownBy(() -> cardPaymentService.settleFromGateway(cardCompleteRequest(BigDecimal.valueOf(180000)), "another-pg-id"))
                .hasMessage("카드 통보의 PG 원거래가 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        then(paymentCompletionService).shouldHaveNoInteractions();
        then(paymentExpirationService).shouldHaveNoInteractions();
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
