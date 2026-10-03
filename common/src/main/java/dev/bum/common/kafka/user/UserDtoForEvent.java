package dev.bum.common.kafka.user;

import dev.bum.common.kafka.enums.TopicEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDtoForEvent {
    private TopicEventType eventType; // CREATE, DELETE
    private Long id;
    private String userId;
    @ToString.Exclude
    private String password;
    // 비밀번호 찾기를 통한 재설정 완료 이벤트에서만 설정한다.
    private Boolean passwordReset;
    private String role;
    private String status;
    private Boolean isBlacklisted;
    private LocalDate blacklistedUntil;
}
