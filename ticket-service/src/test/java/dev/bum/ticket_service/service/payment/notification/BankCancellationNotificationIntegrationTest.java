package dev.bum.ticket_service.service.payment.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.sql.init.mode=never", "app.payment-gateway.merchant-id=1"})
@ContextConfiguration(classes = BankCancellationNotificationIntegrationTest.Config.class)
@Import({BankCancellationNotificationService.class, org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BankCancellationNotificationIntegrationTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackageClasses = GatewayBankCancellationReceipt.class)
    @EnableJpaRepositories(basePackageClasses = GatewayBankCancellationReceiptJpaRepository.class)
    static class Config { }

    @Autowired BankCancellationNotificationService service;
    @Autowired ObjectMapper json;
    @MockitoSpyBean GatewayBankCancellationReceiptJpaRepository receipts;
    @MockitoBean PaymentJpaRepository payments;
    @MockitoBean VirtualAccountCancelOutboxJpaRepository cancellations;
    Payment payment;

    @BeforeEach
    void setup() {
        receipts.deleteAll();
        payment = mock(Payment.class);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getUserId()).thenReturn("buyer");
        when(payment.getReservation()).thenReturn(reservation);
        when(payment.getPgPaymentId()).thenReturn("20261110550e8400-e29b-41d4-a716-446655440000");
        VirtualAccountCancelOutbox pending = mock(VirtualAccountCancelOutbox.class);
        when(cancellations.findIdByPaymentNo("PAY-1")).thenReturn(Optional.of(1L));
        when(cancellations.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
        when(payment.getMethod()).thenReturn(PaymentMethod.BANK_TRANSFER);
        when(payment.getStatus()).thenReturn(PaymentStatus.CANCELLED);
        when(payment.getAmount()).thenReturn(100);
        when(payment.getAccountNumber()).thenReturn("1111-123456");
        when(payment.getBankName()).thenReturn(BankCompany.KB.getBankName());
        when(payments.findByPaymentNoForUpdate("PAY-1")).thenReturn(Optional.of(payment));
    }

    @Test
    void successCommitsAReceiptWithoutRepeatingLocalReservationCancellation() {
        var event = event("CANCELLED", "PG_MERCHANT_CANCELLED");
        assertThat(service.receive(event).matches(event)).isTrue();
        assertThat(receipts.count()).isEqualTo(1);
        verify(payment, never()).cancel();
        assertThat(json.convertValue(receipts.findById(event.eventId()).orElseThrow().getPayload(), BankCancellationNotification.class))
                .isEqualTo(event);
    }

    @Test
    void aLostAcknowledgementReturnsTheSameDurableResult() {
        var event = event("CANCELLED", "PG_MERCHANT_CANCELLED");
        var first = service.receive(event);
        assertThat(service.receive(event)).isEqualTo(first);
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithAnotherResultOrReasonIsRejected() {
        service.receive(event("CANCELLED", "PG_MERCHANT_CANCELLED"));
        assertThatThrownBy(() -> service.receive(event("REJECTED", "ALREADY_DEPOSITED"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.receive(event("CANCELLED", "CHANGED"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void rejectionIsAcknowledgedWithoutReopeningReservationOrClaimingFundsReturn() {
        var event = event("REJECTED", "ALREADY_DEPOSITED");
        assertThat(service.receive(event).outcome()).isEqualTo("REJECTED");
        assertThat(service.receive(event).matches(event)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(receipts.count()).isEqualTo(1);
        verify(payment, never()).cancel();
    }

    @Test
    void anotherMerchantIsRejectedBeforeLocalPaymentAccess() {
        var a = event("CANCELLED", "PG_MERCHANT_CANCELLED");
        var other = new BankCancellationNotification(a.version(), a.eventId(), 2L, a.pgPaymentId(), a.merchantPaymentNo(),
                a.buyerReference(), a.paymentMethod(), a.amount(), a.accountNumber(), a.bankCompany(), a.outcome(),
                a.reason(), a.cancelledAt(), false);
        assertThatThrownBy(() -> service.receive(other)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void amountAndAccountAreValidatedBeforeReceiptStorage() {
        when(payment.getAmount()).thenReturn(101);
        assertThatThrownBy(() -> service.receive(event("CANCELLED", "PG_MERCHANT_CANCELLED"))).isInstanceOf(IllegalArgumentException.class);
        when(payment.getAmount()).thenReturn(100);
        when(payment.getAccountNumber()).thenReturn("other");
        assertThatThrownBy(() -> service.receive(event("CANCELLED", "PG_MERCHANT_CANCELLED"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(receipts.count()).isZero();
    }

    @Test
    void successCannotSilentlyCancelAPaymentWhoseReservationWasNotCancelled() {
        when(payment.getStatus()).thenReturn(PaymentStatus.WAITING_DEPOSIT);
        assertThatThrownBy(() -> service.receive(event("CANCELLED", "PG_MERCHANT_CANCELLED"))).isInstanceOf(IllegalStateException.class);
        assertThat(receipts.count()).isZero();
    }

    @Test
    void receiptFailureDoesNotAcknowledgeAndTheSameEventCanBeRetried() {
        var event = event("CANCELLED", "PG_MERCHANT_CANCELLED");
        doThrow(new DataIntegrityViolationException("simulated")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> service.receive(event)).isInstanceOf(DataIntegrityViolationException.class);
        reset(receipts);
        assertThat(receipts.count()).isZero();
        assertThat(service.receive(event).matches(event)).isTrue();
        assertThat(receipts.count()).isEqualTo(1);
    }

    BankCancellationNotification event(String outcome, String reason) {
        String id = "20261110550e8400-e29b-41d4-a716-446655440000";
        return new BankCancellationNotification(1, id + ":CANCEL:INITIAL", 1L, id, "PAY-1", "buyer",
                PaymentMethod.BANK_TRANSFER, new BigDecimal("100"), "1111-123456", BankCompany.KB, outcome, reason,
                outcome.equals("CANCELLED") ? LocalDateTime.of(2026, 11, 10, 15, 0) : null, false);
    }
}
