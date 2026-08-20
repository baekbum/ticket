package dev.bum.ticket_service.service;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.service.payment.CardPaymentService;
import dev.bum.ticket_service.service.payment.PaymentService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private CardPaymentService cardPaymentService;

    @Mock
    private VirtualAccountPaymentService virtualAccountPaymentService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("카드 결제 완료 요청을 카드 결제 서비스로 위임한다")
    void delegate_card_completion() {
        CardPaymentCompleteRequest request = CardPaymentCompleteRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .userId("user01")
                .amount(BigDecimal.valueOf(180000))
                .build();
        PaymentResponse expectedResponse = paidResponse(PaymentMethod.CREDIT_CARD);

        given(cardPaymentService.completeFromGateway(request)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.completeCardFromGateway(request);

        assertThat(response).isEqualTo(expectedResponse);
        then(cardPaymentService).should().completeFromGateway(request);
    }

    @Test
    @DisplayName("카드 결제 실패 요청을 카드 결제 서비스로 위임한다")
    void delegate_card_failure() {
        CardPaymentFailRequest request = CardPaymentFailRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .userId("user01")
                .amount(BigDecimal.valueOf(180000))
                .failureReason("카드 승인 실패")
                .build();
        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo(request.getPaymentNo())
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.FAILED)
                .amount(180000)
                .build();

        given(cardPaymentService.failFromGateway(request)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.failCardFromGateway(request);

        assertThat(response).isEqualTo(expectedResponse);
        then(cardPaymentService).should().failFromGateway(request);
    }

    @Test
    @DisplayName("가상계좌 발급 반영 요청을 무통장 결제 서비스로 위임한다")
    void delegate_virtual_account_issued() {
        VirtualAccountIssuedRequest request = VirtualAccountIssuedRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .amount(BigDecimal.valueOf(180000))
                .bankName("KB국민은행")
                .accountNumber("1111-1234-123456")
                .expiresAt(LocalDateTime.of(2099, 7, 27, 23, 59, 59))
                .build();
        PaymentResponse expectedResponse = waitingDepositResponse();

        given(virtualAccountPaymentService.applyIssuedFromGateway(request)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.applyVirtualAccountIssued(request);

        assertThat(response).isEqualTo(expectedResponse);
        then(virtualAccountPaymentService).should().applyIssuedFromGateway(request);
    }

    @Test
    @DisplayName("가상계좌 입금 완료 요청을 무통장 결제 서비스로 위임한다")
    void delegate_virtual_account_deposit_completion() {
        VirtualAccountDepositCompleteRequest request = VirtualAccountDepositCompleteRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-1234-123456")
                .depositorName("아이유")
                .amount(BigDecimal.valueOf(180000))
                .depositedAt(LocalDateTime.of(2026, 8, 19, 12, 0))
                .build();
        PaymentResponse expectedResponse = paidResponse(PaymentMethod.BANK_TRANSFER);

        given(virtualAccountPaymentService.completeDepositFromGateway(request)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.completeVirtualAccountDepositFromGateway(request);

        assertThat(response).isEqualTo(expectedResponse);
        then(virtualAccountPaymentService).should().completeDepositFromGateway(request);
    }

    @Test
    @DisplayName("가상계좌 만료 이벤트를 무통장 결제 서비스로 위임한다")
    void delegate_virtual_account_expiration() {
        VirtualAccountExpiredEvent event = VirtualAccountExpiredEvent.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .amount(BigDecimal.valueOf(180000))
                .expiredAt(LocalDateTime.of(2026, 8, 20, 23, 59, 59))
                .build();
        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentNo(event.getPaymentNo())
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.EXPIRED)
                .amount(180000)
                .build();

        given(virtualAccountPaymentService.expireFromGateway(event)).willReturn(expectedResponse);

        PaymentResponse response = paymentService.expireVirtualAccountFromGateway(event);

        assertThat(response).isEqualTo(expectedResponse);
        then(virtualAccountPaymentService).should().expireFromGateway(event);
    }

    private PaymentResponse paidResponse(PaymentMethod paymentMethod) {
        return PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(paymentMethod)
                .status(PaymentStatus.PAID)
                .amount(180000)
                .build();
    }

    private PaymentResponse waitingDepositResponse() {
        return PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-1234-123456")
                .build();
    }
}
