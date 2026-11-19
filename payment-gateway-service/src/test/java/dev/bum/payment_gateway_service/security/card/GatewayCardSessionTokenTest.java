package dev.bum.payment_gateway_service.security.card;

import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.Merchant;
import dev.bum.common.service.paymentgateway.card.GatewayCardSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayCardSessionTokenTest {

    final String secret = "dedicated-card-session-test-key-at-least-32-bytes";
    final Instant now = Instant.parse("2026-11-15T03:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    GatewayPayment payment(Instant deadline) {
        GatewayPayment payment = mock(GatewayPayment.class);
        Merchant merchant = mock(Merchant.class);
        when(merchant.getMerchantId()).thenReturn(1L);
        when(payment.getMerchant()).thenReturn(merchant);
        when(payment.getPgPaymentId()).thenReturn("pg-1");
        when(payment.getMerchantPaymentNo()).thenReturn("PAY-1");
        when(payment.getBuyerReference()).thenReturn("buyer");
        when(payment.getAmount()).thenReturn(BigDecimal.TEN);
        when(payment.getStatus()).thenReturn(GatewayPaymentStatus.READY);
        when(payment.getExpiresAt()).thenReturn(LocalDateTime.ofInstant(deadline, ZoneId.of("Asia/Seoul")));
        return payment;
    }

    @Test void approvalScopeEndsAtTheEarlierOfFiveMinutesAndTheOriginalDeadline() {
        GatewayCardSessionToken tokens = new GatewayCardSessionToken(secret, clock);
        GatewayCardSessionResponse session = tokens.issue(payment(now.plusSeconds(60)), true);
        assertThat(session.expiresAt()).isEqualTo(now.plusSeconds(60));
        assertThat(tokens.verify(session.paymentSession(), "buyer", "pg-1", true).merchantId()).isEqualTo(1);
        assertThat(tokens.issue(payment(now.plusSeconds(600)), true).expiresAt()).isEqualTo(now.plusSeconds(300));
        assertThat(session.toString()).doesNotContain(session.paymentSession());
    }

    @Test void queryScopeCanRecoverAfterThePaymentDeadlineButCannotExecuteNewApproval() {
        GatewayCardSessionToken tokens = new GatewayCardSessionToken(secret, clock);
        GatewayCardSessionResponse session = tokens.issue(payment(now.minusSeconds(1)), true);
        assertThat(session.canApprove()).isFalse();
        assertThat(tokens.verify(session.paymentSession(), "buyer", "pg-1", false).canApprove()).isFalse();
        assertThatThrownBy(() -> tokens.verify(session.paymentSession(), "buyer", "pg-1", true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void signaturesUsersTransactionsExpiryAndCallerRequestedQueryScopeAreEnforced() {
        GatewayCardSessionToken tokens = new GatewayCardSessionToken(secret, clock);
        String token = tokens.issue(payment(now.plusSeconds(600)), true).paymentSession();
        assertThatThrownBy(() -> tokens.verify(token, "other", "pg-1", true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tokens.verify(token, "buyer", "pg-2", true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tokens.verify(token + "invalid", "buyer", "pg-1", false)).isInstanceOf(AccessDeniedException.class);
        GatewayCardSessionToken later = new GatewayCardSessionToken(secret, Clock.offset(clock, Duration.ofMinutes(6)));
        assertThatThrownBy(() -> later.verify(token, "buyer", "pg-1", false)).isInstanceOf(AccessDeniedException.class);
        GatewayCardSessionToken otherKey = new GatewayCardSessionToken("another-separate-test-key-at-least-32-bytes", clock);
        assertThatThrownBy(() -> otherKey.verify(token, "buyer", "pg-1", false)).isInstanceOf(AccessDeniedException.class);
        String query = tokens.issue(payment(now.plusSeconds(600)), false).paymentSession();
        assertThatThrownBy(() -> tokens.verify(query, "buyer", "pg-1", true)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void missingDedicatedConfigurationCannotIssueAnySession() {
        GatewayCardSessionToken tokens = new GatewayCardSessionToken("", clock);
        assertThatThrownBy(() -> tokens.issue(payment(now.plusSeconds(60)), true)).isInstanceOf(IllegalStateException.class);
    }
}
