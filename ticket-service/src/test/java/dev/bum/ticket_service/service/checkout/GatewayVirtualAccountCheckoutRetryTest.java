package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.exception.payment.GatewayAccountIssueUnconfirmedException;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.service.checkout.payment.*;
import feign.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.*;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayVirtualAccountCheckoutRetryTest {
    @Mock GatewayVirtualAccountCheckoutCommands commands;
    @Mock PaymentGatewayTransactionClient gateway;
    @InjectMocks GatewayVirtualAccountCheckoutService issuance;
    final String id = "20261112550e8400-e29b-41d4-a716-446655440000";
    final GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(1L, "PAY-1", "owner",
            PaymentMethod.BANK_TRANSFER, BigDecimal.valueOf(100), LocalDateTime.of(2026, 11, 13, 23, 59, 59), BankCompany.KB);
    final PaymentResponse local = PaymentResponse.builder().paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER).build();

    @Test void theFourthAttemptMaySucceedWithoutReturningAnEarlierFailureToTheBrowser() {
        GatewayPaymentPrepareResponse prepared = mock(GatewayPaymentPrepareResponse.class);
        GatewayAccountIssueResponse issued = mock(GatewayAccountIssueResponse.class);
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(null, true));
        when(gateway.prepare(request)).thenThrow(httpFailure(503), httpFailure(502), httpFailure(504)).thenReturn(prepared);
        when(commands.prepared("PAY-1", prepared)).thenReturn(id);
        when(gateway.issue(eq(id), any())).thenReturn(issued);
        when(commands.issued("PAY-1", issued)).thenReturn(local);
        assertThat(issuance.issue("PAY-1")).isSameAs(local);
        verify(gateway, times(4)).prepare(request);
        verify(gateway).issue(eq(id), any());
        verify(commands, times(4)).findPreparation("PAY-1");
    }

    @Test void aReadTimeoutRetriesTheSameAlreadyPreparedPgPayment() {
        GatewayAccountIssueResponse issued = mock(GatewayAccountIssueResponse.class);
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(id, true));
        RuntimeException timeout = new RetryableException(-1, "timeout", Request.HttpMethod.POST,
                new SocketTimeoutException("response lost"), (Long) null, httpRequest());
        when(gateway.issue(eq(id), any())).thenThrow(timeout).thenReturn(issued);
        when(commands.issued("PAY-1", issued)).thenReturn(local);
        assertThat(issuance.issue("PAY-1")).isSameAs(local);
        verify(gateway, never()).prepare(any());
        ArgumentCaptor<GatewayAccountIssueRequest> payloads = ArgumentCaptor.forClass(GatewayAccountIssueRequest.class);
        verify(gateway, times(2)).issue(eq(id), payloads.capture());
        assertThat(payloads.getAllValues().stream().distinct()).hasSize(1);
    }

    @Test void aTransientLocalCommitFailureReloadsThePgIdBeforeRetrying() {
        GatewayPaymentPrepareResponse prepared = mock(GatewayPaymentPrepareResponse.class);
        GatewayAccountIssueResponse issued = mock(GatewayAccountIssueResponse.class);
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(null, true), preparation(id, true));
        when(gateway.prepare(request)).thenReturn(prepared);
        when(commands.prepared("PAY-1", prepared)).thenReturn(id);
        when(gateway.issue(eq(id), any())).thenReturn(issued);
        when(commands.issued("PAY-1", issued)).thenThrow(new CannotAcquireLockException("temporary lock"))
                .thenReturn(local);
        assertThat(issuance.issue("PAY-1")).isSameAs(local);
        verify(gateway).prepare(request);
        verify(gateway, times(2)).issue(eq(id), any());
    }

    @Test void aPermanentLocalWriteFailureIsNotRetried() {
        GatewayAccountIssueResponse issued = mock(GatewayAccountIssueResponse.class);
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(id, true));
        when(gateway.issue(eq(id), any())).thenReturn(issued);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("different account");
        when(commands.issued("PAY-1", issued)).thenThrow(failure);
        assertThatThrownBy(() -> issuance.issue("PAY-1")).isSameAs(failure);
        verify(gateway).issue(eq(id), any());
        verify(commands).findPreparation("PAY-1");
    }

    @Test void aCompletedPaymentReturnsItsExistingResultWithoutAnotherHttpRequest() {
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(id, false));
        assertThat(issuance.issue("PAY-1")).isSameAs(local);
        verifyNoInteractions(gateway);
    }

    @Test void interruptionStopsFurtherAttemptsAndPreservesTheInterruptFlag() {
        when(commands.findPreparation("PAY-1")).thenReturn(preparation(id, true));
        when(gateway.issue(eq(id), any())).thenThrow(httpFailure(503));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> issuance.issue("PAY-1")).isInstanceOf(GatewayAccountIssueUnconfirmedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(gateway).issue(eq(id), any());
        } finally { Thread.interrupted(); }
    }

    GatewayAccountIssuePreparation preparation(String pgId, boolean pending) {
        return new GatewayAccountIssuePreparation(request, pgId, pending, local);
    }

    FeignException httpFailure(int code) {
        return FeignException.errorStatus("gateway", Response.builder().status(code).reason("failure")
                .request(httpRequest()).headers(Map.of()).body("{}", StandardCharsets.UTF_8).build());
    }

    Request httpRequest() {
        return Request.create(Request.HttpMethod.POST, "http://pg/issue", Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
