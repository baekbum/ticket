package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountIssueResponse;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.checkout.payment.VirtualAccountCheckoutPaymentHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountCheckoutPaymentHandlerTest {

    @Mock
    private PaymentGatewayVirtualAccountClient paymentGatewayVirtualAccountClient;

    @InjectMocks
    private VirtualAccountCheckoutPaymentHandler handler;

    @Test
    @DisplayName("무통장 결제 confirm 처리 시 gateway에서 가상계좌를 발급받아 결제에 반영한다")
    void process_virtual_account_payment() {
        Event event = event();
        Reservation reservation = reservation(event);
        Payment payment = payment(reservation);
        CheckoutConfirmRequest request = request("KB");

        given(paymentGatewayVirtualAccountClient.issue(org.mockito.ArgumentMatchers.any()))
                .willReturn(virtualAccountIssueResponse());

        handler.process(request, payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(payment.getBankName()).isEqualTo("KB국민은행");
        assertThat(payment.getAccountNumber()).isEqualTo("1111-2222-3333-4444");
        assertThat(payment.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 23, 59, 59));
        then(paymentGatewayVirtualAccountClient).should().issue(org.mockito.ArgumentMatchers.argThat(argument ->
                argument.getPaymentNo().equals("PAY-20260727120000-abcdef123456")
                        && argument.getBankCompany() == BankCompany.KB
                        && argument.getAmount().compareTo(BigDecimal.valueOf(180000)) == 0
                        && argument.getEventDateTime().equals(event.getEventDateTime())
                        && Boolean.FALSE.equals(argument.getTicketPaymentApplyRequired())
        ));
    }

    @Test
    @DisplayName("은행 코드가 없으면 무통장 결제 confirm 처리를 거부한다")
    void reject_missing_bank_code() {
        assertThatThrownBy(() -> handler.process(request(" "), payment(reservation(event()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("은행 코드가 필요합니다.");
    }

    @Test
    @DisplayName("지원하지 않는 은행 코드면 무통장 결제 confirm 처리를 거부한다")
    void reject_invalid_bank_code() {
        assertThatThrownBy(() -> handler.process(request("UNKNOWN"), payment(reservation(event()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 은행 코드입니다.");
    }

    private CheckoutConfirmRequest request(String bankCode) {
        return CheckoutConfirmRequest.builder()
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .bankCode(bankCode)
                .build();
    }

    private GatewayVirtualAccountIssueResponse virtualAccountIssueResponse() {
        return GatewayVirtualAccountIssueResponse.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .amount(BigDecimal.valueOf(180000))
                .expiresAt(LocalDateTime.of(2026, 9, 18, 23, 59, 59))
                .issued(true)
                .message("가상계좌가 발급되었습니다.")
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

    private Reservation reservation(Event event) {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId("user01")
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment payment(Reservation reservation) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.READY)
                .amount(180000)
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }
}
