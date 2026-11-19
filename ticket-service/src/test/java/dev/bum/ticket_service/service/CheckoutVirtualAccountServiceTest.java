package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.CheckoutVirtualAccountService;
import org.junit.jupiter.api.Test;

import java.time.*;
import static org.assertj.core.api.Assertions.*;

class CheckoutVirtualAccountServiceTest {

    final LocalDate today = LocalDate.of(2026, 11, 11);
    final CheckoutVirtualAccountService service = new CheckoutVirtualAccountService(
            Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test void persistsAnIntentAndTheOldTicksyDeadlineRuleWithoutHttpOrAnAccountNumber() {
        Payment payment = payment(today.plusDays(5));
        service.issue(request("KB"), payment);
        assertThat(payment.getGatewayBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(payment.isGatewayIssuePending()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getAccountNumber()).isNull();
        assertThat(payment.getPgPaymentId()).isNull();
        assertThat(payment.getGatewayExpiresAt()).isEqualTo(today.plusDays(1).atTime(23, 59, 59));
    }

    @Test void tomorrowShowsDeadlineTodayAndSameDayBankTransferIsRejected() {
        Payment payment = payment(today.plusDays(1));
        service.issue(request(" kb "), payment);
        assertThat(payment.getGatewayExpiresAt()).isEqualTo(today.atTime(23, 59, 59));
        assertThatThrownBy(() -> service.issue(request("KB"), payment(today)))
                .hasMessage("공연 당일에는 무통장 입금을 사용할 수 없습니다.");
    }

    @Test void retryKeepsTheFirstDeadlineAndRejectsAnotherBank() {
        Payment payment = payment(today.plusDays(5));
        LocalDateTime original = today.plusDays(1).atTime(23, 59, 59);
        payment.planGatewayAccount(BankCompany.KB, original);
        service.issue(request("KB"), payment);
        assertThat(payment.getGatewayExpiresAt()).isEqualTo(original);
        assertThatThrownBy(() -> service.issue(request("SHINHAN"), payment)).isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.getGatewayBankCompany()).isEqualTo(BankCompany.KB);
    }

    @Test void rejectsMissingOrUnknownBank() {
        assertThatThrownBy(() -> service.issue(request(" "), payment(today.plusDays(5))))
                .hasMessage("은행 코드가 필요합니다.");
        assertThatThrownBy(() -> service.issue(request("UNKNOWN"), payment(today.plusDays(5))))
                .hasMessage("지원하지 않는 은행 코드입니다.");
    }

    CheckoutConfirmRequest request(String bank) {
        return CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).bankCode(bank).build();
    }

    Payment payment(LocalDate date) {
        Reservation reservation = Reservation.builder().userId("owner")
                .event(Event.builder().eventDateTime(date.atTime(18, 0)).build()).build();
        return Payment.builder().reservation(reservation).paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER)
                .amount(100).requestedAt(LocalDateTime.now()).build();
    }
}
