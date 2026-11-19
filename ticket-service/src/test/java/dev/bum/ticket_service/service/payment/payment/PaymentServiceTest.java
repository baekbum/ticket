package dev.bum.ticket_service.service.payment.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentService;
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

        given(virtualAccountPaymentService.completeDepositFromGateway(request, "owner")).willReturn(expectedResponse);

        PaymentResponse response = paymentService.completeVirtualAccountDepositFromGateway(request, "owner");

        assertThat(response).isEqualTo(expectedResponse);
        then(virtualAccountPaymentService).should().completeDepositFromGateway(request, "owner");
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

}
