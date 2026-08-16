package dev.bum.auth_service.kafka;

import dev.bum.auth_service.service.AuthService;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final AuthService authService;

    @KafkaListener(topics = "${topic.user.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(UserDtoForEvent event) {
        log.info(">>>> Kafka로부터 메시지 도착");

        if (event.getEventType() == TopicEventType.CREATE) {
            authService.insertUserTopic(event);
        } else if (event.getEventType() == TopicEventType.UPDATE) {
            authService.updateUserTopic(event);
        } else {
            authService.deleteUserTopic(event);
        }

        log.info(">>>> 권한 서비스 DB 동기화 완료: userId={}", event.getUserId());
    }
}
