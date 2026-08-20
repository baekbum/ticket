package dev.bum.ticket_service.kafka.payment;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.ticket_service.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAccountExpiredConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "${topic.payment.virtual-account.expired.name}",
            groupId = "${topic.payment.virtual-account.expired.group-id}"
    )
    public void consume(VirtualAccountExpiredEvent event) {
        log.info("가상계좌 만료 이벤트 수신: paymentNo={}", event.getPaymentNo());
        paymentService.expireVirtualAccountFromGateway(event);
    }
}
