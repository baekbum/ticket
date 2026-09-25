package dev.bum.auth_service.kafka;

import dev.bum.common.kafka.login.LoginLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${topic.login.log.name}")
    private String loginLogTopicName;

    public void send(LoginLogEvent event) {
        kafkaTemplate.send(loginLogTopicName, event.getEventId(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info(
                                "Login log event sent. eventId={}, loginId={}, result={}",
                                event.getEventId(),
                                event.getLoginId(),
                                event.getResult()
                        );
                    } else {
                        log.warn(
                                "Login log event failed. eventId={}, loginId={}, result={}",
                                event.getEventId(),
                                event.getLoginId(),
                                event.getResult(),
                                throwable
                        );
                    }
                });
    }
}
