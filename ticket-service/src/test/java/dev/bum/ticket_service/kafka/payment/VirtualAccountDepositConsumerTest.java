package dev.bum.ticket_service.kafka.payment;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.service.payment.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class VirtualAccountDepositConsumerTest {

    @Test
    @DisplayName("가상계좌 입금 완료 이벤트를 수신하면 결제 완료 서비스를 호출한다")
    void consume_virtual_account_deposit_completed_event() {
        PaymentService paymentService = mock(PaymentService.class);
        VirtualAccountDepositConsumer consumer = new VirtualAccountDepositConsumer(paymentService);
        VirtualAccountDepositCompletedEvent event = VirtualAccountDepositCompletedEvent.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .depositorName("아이유")
                .amount(BigDecimal.valueOf(180000))
                .depositedAt(LocalDateTime.of(2026, 8, 19, 12, 0))
                .build();

        consumer.consume(event);

        then(paymentService).should().completeVirtualAccountDepositFromGateway(event);
    }
}
