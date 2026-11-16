package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.service.checkout.*;
import dev.bum.ticket_service.service.checkout.payment.GatewayVirtualAccountCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.GatewayCardCheckoutService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServiceOrchestrationTest {
    final CheckoutTransactionService commands = mock(CheckoutTransactionService.class);
    final GatewayVirtualAccountCheckoutService accounts = mock(GatewayVirtualAccountCheckoutService.class);
    final PaymentGatewayCardClient cards = mock(PaymentGatewayCardClient.class);
    final GatewayCardCheckoutService cardCheckout = mock(GatewayCardCheckoutService.class);
    final CheckoutService checkout = new CheckoutService(commands, accounts, cardCheckout, cards, mock(dev.bum.ticket_service.service.checkout.payment.GatewayCardMethodChangeCheckoutService.class));

    @Test void bankIssuanceRunsOnlyAfterLocalConfirmReturnsAndItsResultIsReturned() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        when(commands.confirm("owner", "queue", request, false)).thenReturn(PaymentResponse.builder()
                .paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.READY).build());
        PaymentResponse issued = PaymentResponse.builder().paymentNo("PAY-1").pgPaymentId("pg-id")
                .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.WAITING_DEPOSIT).build();
        when(accounts.issue("PAY-1")).thenReturn(issued);
        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(issued);
        InOrder order = inOrder(commands, accounts);
        order.verify(commands).findCardSwitch("owner", request);
        order.verify(commands).confirm("owner", "queue", request, false);
        order.verify(accounts).issue("PAY-1");
        verifyNoInteractions(cards);
    }

    @Test void cardCheckoutDoesNotUseTheBankGateway() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).build();
        PaymentResponse card = PaymentResponse.builder().method(PaymentMethod.CREDIT_CARD).build();
        when(commands.confirm("owner", "queue", request, false)).thenReturn(card);
        when(cardCheckout.prepare("owner", null)).thenReturn(card);
        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(card);
        verify(cardCheckout).prepare("owner", null);
        verifyNoInteractions(accounts, cards);
    }

    @Test void cardSwitchHistoryIsCheckedBeforeTheLocalTransactionAndUnknownHistoryDoesNotAuthorizeSwitch() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        when(commands.findCardSwitch("owner", request)).thenReturn(new CheckoutTransactionService.CardSwitchCandidate("PAY-1", false));
        when(cards.hasApprovalHistory("PAY-1")).thenReturn(false);
        when(commands.confirm("owner", "queue", request, true)).thenThrow(new IllegalStateException("local validation"));
        assertThatThrownBy(() -> checkout.confirm("owner", "queue", request)).hasMessage("local validation");
        InOrder order = inOrder(commands, cards);
        order.verify(commands).findCardSwitch("owner", request);
        order.verify(cards).hasApprovalHistory("PAY-1");
        order.verify(commands).confirm("owner", "queue", request, true);
        when(cards.hasApprovalHistory("PAY-1")).thenReturn(null);
        when(commands.confirm("owner", "queue", request, false)).thenThrow(new IllegalStateException("unverified history"));
        assertThatThrownBy(() -> checkout.confirm("owner", "queue", request)).hasMessage("unverified history");
        verifyNoInteractions(accounts);
    }
}
