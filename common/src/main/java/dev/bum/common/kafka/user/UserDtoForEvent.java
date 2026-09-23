package dev.bum.common.kafka.user;

import dev.bum.common.kafka.enums.TopicEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private String status;
    private Boolean isBlacklisted;
    private LocalDate blacklistedUntil;
}
