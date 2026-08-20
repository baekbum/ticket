package dev.bum.payment_gateway_service.kafka.virtualAccount;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAccountExpiredEventProducer {

    private final KafkaTemplate<String, VirtualAccountExpiredEvent> kafkaTemplate;

    @Value("${topic.payment.virtual-account.expired.name:virtual-account-expired}")
    private String virtualAccountExpiredTopic;

    public CompletableFuture<?> sendExpired(VirtualAccountExpiredEvent event) {
        return kafkaTemplate.send(virtualAccountExpiredTopic, event.getPaymentNo(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info("가상계좌 만료 이벤트 발행 성공: paymentNo={}", event.getPaymentNo());
                    } else {
                        log.error("가상계좌 만료 이벤트 발행 실패: paymentNo={}", event.getPaymentNo(), throwable);
                    }
                });
    }
}
