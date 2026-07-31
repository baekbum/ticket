package dev.bum.ticket_service.kafka.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("결제 완료 이벤트는 paymentNo를 key로 사용하고 필요한 필드를 JSON payload에 담아 발행한다")
    void send_payment_completed_publishes_expected_kafka_record() {
        PaymentEventProducer producer = new PaymentEventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "paymentCompletedTopic", "payment-completed-test");
        CompletableFuture<SendResult<String, String>> completedFuture = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.eq("payment-completed-test"),
                org.mockito.ArgumentMatchers.eq("PAY-1"),
                org.mockito.ArgumentMatchers.anyString()
        )).willReturn(completedFuture);

        producer.sendPaymentCompleted("PAY-1", 10L, "ORDER-1", 180000);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        then(kafkaTemplate).should().send(
                org.mockito.ArgumentMatchers.eq("payment-completed-test"),
                org.mockito.ArgumentMatchers.eq("PAY-1"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue())
                .contains("\"paymentNo\":\"PAY-1\"")
                .contains("\"reservationId\":10")
                .contains("\"orderId\":\"ORDER-1\"")
                .contains("\"amount\":180000");
    }
}
