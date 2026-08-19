package dev.bum.ticket_service.service;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountPaymentServiceTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @InjectMocks
    private VirtualAccountPaymentService virtualAccountPaymentService;

    @Test
    @DisplayName("payment-gateway 가상계좌 발급 정보를 입금 대기 상태로 반영한다")
    void apply_virtual_account_issued_success() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.BANK_TRANSFER, PaymentStatus.READY);
        VirtualAccountIssuedRequest request = virtualAccountIssuedRequest(BigDecimal.valueOf(180000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        PaymentResponse response = virtualAccountPaymentService.applyIssuedFromGateway(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).isEqualTo("1111-1234-123456");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
    }

    @Test
    @DisplayName("payment-gateway 가상계좌 발급 금액이 다르면 결제 상태를 유지한다")
    void apply_virtual_account_issued_amount_mismatch() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = payment(reservation, PaymentMethod.BANK_TRANSFER, PaymentStatus.READY);
        VirtualAccountIssuedRequest request = virtualAccountIssuedRequest(BigDecimal.valueOf(170000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> virtualAccountPaymentService.applyIssuedFromGateway(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 금액이 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("payment-gateway 입금 완료 이벤트 수신 시 결제와 예매를 완료 처리한다")
    void complete_virtual_account_deposit_from_gateway_success() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2099, 7, 27, 23, 59, 59));
        VirtualAccountDepositCompletedEvent depositEvent = virtualAccountDepositCompletedEvent(BigDecimal.valueOf(180000));
        PaymentResponse paymentResponse = paidResponse(PaymentMethod.BANK_TRANSFER);

        given(paymentJpaRepository.findByPaymentNoForUpdate(depositEvent.getPaymentNo())).willReturn(Optional.of(payment));
        given(paymentCompletionService.completeDeposit(payment, depositEvent.getDepositedAt(), depositEvent.getDepositorName()))
                .willReturn(paymentResponse);

        PaymentResponse response = virtualAccountPaymentService.completeDepositFromGateway(depositEvent);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        then(paymentCompletionService).should().completeDeposit(payment, depositEvent.getDepositedAt(), depositEvent.getDepositorName());
    }

    @Test
    @DisplayName("payment-gateway 입금 완료 이벤트 금액이 다르면 결제 상태를 유지한다")
    void complete_virtual_account_deposit_from_gateway_amount_mismatch() {
        Reservation reservation = reservation(event(), "user01");
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.of(2099, 7, 27, 23, 59, 59));
        VirtualAccountDepositCompletedEvent depositEvent = virtualAccountDepositCompletedEvent(BigDecimal.valueOf(170000));

        given(paymentJpaRepository.findByPaymentNoForUpdate(depositEvent.getPaymentNo())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> virtualAccountPaymentService.completeDepositFromGateway(depositEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 금액이 일치하지 않습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        then(paymentCompletionService).shouldHaveNoInteractions();
    }

    private VirtualAccountIssuedRequest virtualAccountIssuedRequest(BigDecimal amount) {
        return VirtualAccountIssuedRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .amount(amount)
                .bankName("KB국민은행")
                .accountNumber("1111-1234-123456")
                .expiresAt(LocalDateTime.of(2099, 7, 27, 23, 59, 59))
                .build();
    }

    private VirtualAccountDepositCompletedEvent virtualAccountDepositCompletedEvent(BigDecimal amount) {
        return VirtualAccountDepositCompletedEvent.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .depositorName("아이유")
                .amount(amount)
                .depositedAt(LocalDateTime.of(2026, 8, 19, 12, 0))
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
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(method)
                .status(status)
                .amount(180000)
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment virtualAccountPayment(Reservation reservation, PaymentStatus status, LocalDateTime expiresAt) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
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
