package dev.bum.common.kafka;

import dev.bum.common.kafka.enums.TopicEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDtoForEvent {
    private TopicEventType eventType; // CREATE, DELETE
    private Long id;
    private String userId;
    private String password;
    private String role;
}
