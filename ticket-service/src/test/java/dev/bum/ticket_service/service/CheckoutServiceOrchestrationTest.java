package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.service.checkout.*;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.translate.CardToBankCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.translate.BankToCardCheckoutService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServiceOrchestrationTest {
    final CheckoutTransactionService commands = mock(CheckoutTransactionService.class);
    final VirtualAccountIssuanceCheckoutService accounts = mock(VirtualAccountIssuanceCheckoutService.class);
    final CardCheckoutService cardCheckout = mock(CardCheckoutService.class);
    final CardToBankCheckoutService cardToBank = mock(CardToBankCheckoutService.class);
    final BankToCardCheckoutService bankToCard = mock(BankToCardCheckoutService.class);
    final CheckoutService checkout = new CheckoutService(commands, accounts, cardCheckout, cardToBank, bankToCard);

    @Test void managedCardToBankUsesTheSharedCandidateAndItsDirectionService() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        when(commands.findMethodChangeCandidate("owner", request)).thenReturn(new CheckoutTransactionService.MethodChangeCandidate(
                PaymentMethod.CREDIT_CARD, PaymentMethod.BANK_TRANSFER));
        PaymentResponse response = PaymentResponse.builder().method(PaymentMethod.BANK_TRANSFER).build();
        when(cardToBank.change("owner", "queue", request)).thenReturn(response);

        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(response);
        verify(commands, times(1)).findMethodChangeCandidate("owner", request);
        verify(commands, never()).confirm(any(), any(), any());
        verifyNoInteractions(bankToCard, accounts, cardCheckout);
    }

    @Test void managedBankToCardUsesTheSharedCandidateAndItsDirectionService() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).build();
        when(commands.findMethodChangeCandidate("owner", request)).thenReturn(new CheckoutTransactionService.MethodChangeCandidate(
                PaymentMethod.BANK_TRANSFER, PaymentMethod.CREDIT_CARD));
        PaymentResponse response = PaymentResponse.builder().method(PaymentMethod.CREDIT_CARD).build();
        when(bankToCard.change("owner", "queue", request)).thenReturn(response);

        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(response);
        verify(commands, times(1)).findMethodChangeCandidate("owner", request);
        verify(commands, never()).confirm(any(), any(), any());
        verifyNoInteractions(cardToBank, accounts, cardCheckout);
    }

    @Test void bankIssuanceRunsOnlyAfterLocalConfirmReturnsAndItsResultIsReturned() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        when(commands.confirm("owner", "queue", request)).thenReturn(PaymentResponse.builder()
                .paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.READY).build());
        PaymentResponse issued = PaymentResponse.builder().paymentNo("PAY-1").pgPaymentId("pg-id")
                .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.WAITING_DEPOSIT).build();
        when(accounts.prepare("PAY-1")).thenReturn(PaymentResponse.builder().paymentNo("PAY-1")
                .pgPaymentId("pg-id").method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.READY).build());
        when(accounts.issue("PAY-1")).thenReturn(issued);
        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(issued);
        InOrder order = inOrder(commands, accounts);
        order.verify(commands).findMethodChangeCandidate("owner", request);
        order.verify(commands).confirm("owner", "queue", request);
        order.verify(accounts).prepare("PAY-1");
        order.verify(accounts).issue("PAY-1");
        verifyNoInteractions(cardToBank, bankToCard);
    }

    @Test void cardCheckoutDoesNotUseTheBankGateway() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).build();
        PaymentResponse card = PaymentResponse.builder().method(PaymentMethod.CREDIT_CARD).build();
        when(commands.confirm("owner", "queue", request)).thenReturn(card);
        when(cardCheckout.prepare("owner", null)).thenReturn(card);
        assertThat(checkout.confirm("owner", "queue", request)).isSameAs(card);
        verify(cardCheckout).prepare("owner", null);
        verifyNoInteractions(accounts, cardToBank, bankToCard);
    }

    @Test void aFailedPgPreparationDoesNotContinueToAccountIssuance() {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder().paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        when(commands.confirm("owner", "queue", request)).thenReturn(PaymentResponse.builder()
                .paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.READY).build());
        IllegalStateException failure = new IllegalStateException("PG 준비 실패");
        when(accounts.prepare("PAY-1")).thenThrow(failure);

        assertThatThrownBy(() -> checkout.confirm("owner", "queue", request)).isSameAs(failure);

        verify(accounts, never()).issue(anyString());
    }

}
