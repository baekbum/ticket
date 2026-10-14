package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import dev.bum.ticket_service.service.checkout.payment.CheckoutVirtualAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CheckoutPaymentServiceTest {

    private final CheckoutVirtualAccountService virtualAccountService = mock(CheckoutVirtualAccountService.class);
    private final CheckoutPaymentService checkoutPaymentService = new CheckoutPaymentService(virtualAccountService);

    @Test
    @DisplayName("카드 결제는 승인 전 READY 상태를 유지하고 가상계좌를 발급하지 않는다")
    void card_payment_remains_ready() {
        Payment payment = Payment.builder()
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .build();
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        checkoutPaymentService.process(request, payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(virtualAccountService);
    }

    @Test
    @DisplayName("무통장 결제는 가상계좌를 발급한다")
    void bank_transfer_issues_virtual_account() {
        Payment payment = Payment.builder().method(PaymentMethod.BANK_TRANSFER).build();
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder()
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        checkoutPaymentService.process(request, payment);

        verify(virtualAccountService).issue(request, payment);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("지원하지 않거나 누락된 결제 수단은 거부한다")
    void unsupported_payment_method_is_rejected(PaymentMethod method) {
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder()
                .paymentMethod(method)
                .build();

        assertThatThrownBy(() -> checkoutPaymentService.process(request, Payment.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 결제 수단입니다.");
        verifyNoInteractions(virtualAccountService);
    }
}
