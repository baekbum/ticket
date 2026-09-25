package dev.bum.auth_service.kafka;

import dev.bum.common.kafka.login.LoginLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginLogProducerTest {

    @Test
    @DisplayName("eventId를 메시지 키로 로그인 로그 이벤트를 발행")
    void send_uses_event_id_as_record_key() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        LoginLogProducer producer = new LoginLogProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "loginLogTopicName", "login-log");

        LoginLogEvent event = LoginLogEvent.builder()
                .eventId("event-1")
                .loginId("user01")
                .result("SUCCESS")
                .authMethod("PASSWORD")
                .build();
        @SuppressWarnings("unchecked")
        SendResult<Object, Object> sendResult = mock(SendResult.class);
        given(kafkaTemplate.send("login-log", "event-1", event))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        producer.send(event);

        verify(kafkaTemplate).send("login-log", "event-1", event);
    }
}
