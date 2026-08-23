package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayVirtualAccountRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountPaymentRefundServiceTest {

    @Mock
    private PaymentGatewayVirtualAccountClient paymentGatewayVirtualAccountClient;

    @InjectMocks
    private VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    @Test
    @DisplayName("무통장 전체 환불 요청 후 결제를 전체 환불 상태로 변경한다")
    void refund_all_virtual_account_payment() {
        Payment payment = virtualAccountPayment();

        virtualAccountPaymentRefundService.refundAll(payment, BankCompany.KB, "123-456-7890", "홍길동");

        ArgumentCaptor<GatewayVirtualAccountRefundRequest> captor = ArgumentCaptor.forClass(GatewayVirtualAccountRefundRequest.class);
        then(paymentGatewayVirtualAccountClient).should().refund(captor.capture());
        assertThat(captor.getValue().getPaymentNo()).isEqualTo("PAY-1");
        assertThat(captor.getValue().getRefundBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(captor.getValue().getRefundAccountNumber()).isEqualTo("123-456-7890");
        assertThat(captor.getValue().getRefundAccountHolder()).isEqualTo("홍길동");
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("250000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(payment.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("환불 계좌번호가 없으면 무통장 전체 환불할 수 없다")
    void reject_missing_refund_account_number() {
        Payment payment = virtualAccountPayment();

        assertThatThrownBy(() -> virtualAccountPaymentRefundService.refundAll(payment, BankCompany.KB, null, "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불 계좌번호를 입력해야 합니다.");

        then(paymentGatewayVirtualAccountClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("환불 계좌 예금주명이 없으면 무통장 전체 환불할 수 없다")
    void reject_missing_refund_account_holder() {
        Payment payment = virtualAccountPayment();

        assertThatThrownBy(() -> virtualAccountPaymentRefundService.refundAll(payment, BankCompany.KB, "123-456-7890", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불 계좌 예금주명을 입력해야 합니다.");

        then(paymentGatewayVirtualAccountClient).shouldHaveNoInteractions();
    }

    private Payment virtualAccountPayment() {
        return Payment.builder()
                .paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .requestedAt(LocalDateTime.of(2026, 8, 23, 12, 0))
                .build();
    }
}
