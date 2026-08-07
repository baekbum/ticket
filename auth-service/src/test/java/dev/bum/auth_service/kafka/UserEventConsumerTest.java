package dev.bum.auth_service.kafka;

import dev.bum.auth_service.service.AuthService;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class UserEventConsumerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UserEventConsumer consumer = new UserEventConsumer(authService);

    @Test
    @DisplayName("CREATE 이벤트를 auth service insert로 위임")
    void consume_create_event() {
        UserDtoForEvent event = userEvent(TopicEventType.CREATE);

        consumer.consume(event);

        then(authService).should().insertUserTopic(event);
    }

    @Test
    @DisplayName("처리 실패 예외를 Kafka error handler로 전파")
    void consume_propagates_exception() {
        UserDtoForEvent event = userEvent(TopicEventType.UPDATE);
        willThrow(new IllegalStateException("db error"))
                .given(authService)
                .updateUserTopic(event);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db error");
    }

    private UserDtoForEvent userEvent(TopicEventType eventType) {
        return UserDtoForEvent.builder()
                .eventType(eventType)
                .userId("user01")
                .build();
    }
}
