package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VirtualAccountExpirationSchedulerTest {
    @Test void oneFailedPgLookupDoesNotStarveOtherPaymentsOrTheNextPage() {
        PaymentJpaRepository payments = mock(PaymentJpaRepository.class);
        VirtualAccountExpirationCommandService commands = mock(VirtualAccountExpirationCommandService.class);
        PaymentGatewayTransactionClient pg = mock(PaymentGatewayTransactionClient.class);
        when(payments.findGatewayExpirationCandidateIds(any(), eq(0L), eq(PaymentMethod.BANK_TRANSFER),
                eq(List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT)), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(payments.findGatewayExpirationCandidateIds(any(), eq(2L), eq(PaymentMethod.BANK_TRANSFER),
                eq(List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT)), any(Pageable.class))).thenReturn(List.of(3L));
        when(payments.findGatewayExpirationCandidateIds(any(), eq(3L), eq(PaymentMethod.BANK_TRANSFER),
                eq(List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT)), any(Pageable.class))).thenReturn(List.of());
        for (long id = 1; id <= 3; id++) {
            when(commands.findCandidate(eq(id), any())).thenReturn(new VirtualAccountExpirationCandidate(id, "PAY-" + id, "PG-" + id));
        }
        when(pg.findByPgPaymentId("PG-1")).thenThrow(new IllegalStateException("unavailable"));
        GatewayPaymentPrepareResponse response = mock(GatewayPaymentPrepareResponse.class);
        when(pg.findByPgPaymentId("PG-2")).thenReturn(response);
        when(pg.findByPgPaymentId("PG-3")).thenReturn(response);
        new VirtualAccountExpirationScheduler(payments, commands, pg, Clock.systemUTC()).expirePayments();
        verify(commands, never()).expireIfConfirmed(argThat(candidate -> candidate.paymentId() == 1L), any());
        verify(commands).expireIfConfirmed(argThat(candidate -> candidate.paymentId() == 2L), same(response));
        verify(commands).expireIfConfirmed(argThat(candidate -> candidate.paymentId() == 3L), same(response));
    }
}
