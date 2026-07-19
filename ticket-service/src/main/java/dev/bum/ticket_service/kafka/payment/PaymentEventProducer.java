package dev.bum.ticket_service.kafka.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${topic.payment.completed.name:payment-completed}")
    private String paymentCompletedTopic;

    public void sendPaymentCompleted(String paymentNo, Long reservationId, String orderId, Integer amount) {
        String payload = String.format(
                "{\"paymentNo\":\"%s\",\"reservationId\":%d,\"orderId\":\"%s\",\"amount\":%d}",
                paymentNo,
                reservationId,
                orderId,
                amount
        );

        kafkaTemplate.send(paymentCompletedTopic, paymentNo, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Payment completed event sent: paymentNo={}", paymentNo);
                    } else {
                        log.error("Payment completed event failed: paymentNo={}", paymentNo, ex);
                    }
                });
    }
}
