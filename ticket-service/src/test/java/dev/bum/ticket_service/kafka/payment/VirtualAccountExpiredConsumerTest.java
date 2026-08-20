package dev.bum.ticket_service.kafka.payment;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.service.payment.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class VirtualAccountExpiredConsumerTest {

    @Test
    @DisplayName("가상계좌 만료 이벤트를 수신하면 결제 만료 서비스를 호출한다")
    void consume_virtual_account_expired_event() {
        PaymentService paymentService = mock(PaymentService.class);
        VirtualAccountExpiredConsumer consumer = new VirtualAccountExpiredConsumer(paymentService);
        VirtualAccountExpiredEvent event = VirtualAccountExpiredEvent.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-2222-3333-4444")
                .amount(BigDecimal.valueOf(180000))
                .expiredAt(LocalDateTime.of(2026, 8, 20, 23, 59, 59))
                .build();

        consumer.consume(event);

        then(paymentService).should().expireVirtualAccountFromGateway(event);
    }
}
