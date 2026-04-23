package dev.bum.auth_service.kafka;

import dev.bum.auth_service.service.AuthService;
import dev.bum.common.kafka.UserDtoForEvent;
import dev.bum.common.kafka.enums.TopicEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final AuthService authService;

    @Value("${topic.name}")
    private String userTopic;

    @KafkaListener(topics = "${topic.name}", groupId = "auth-group")
    public void consume(UserDtoForEvent event) {
        log.info(">>>> Kafka로부터 메시지 도착: {}", event);

        try {
            // 이제 이 데이터를 가지고 권한 DB에 Insert 하거나 권한을 부여하는 로직을 실행합니다.
            if (event.getEventType() == TopicEventType.CREATE) {
                authService.insertUserTopic(event);
            } else {
                authService.deleteUserTopic(event);
            }

            log.info(">>>> 권한 서비스 DB 동기화 완료: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error(">>>> 데이터 처리 중 에러 발생: {}", e.getMessage());
        }
    }
}
