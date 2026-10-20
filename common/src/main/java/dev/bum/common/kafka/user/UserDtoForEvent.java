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
    // Outbox 저장 시 한 번 생성하며 재발행·DLT 재처리에서도 같은 키를 사용한다.
    private String idempotencyKey;
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
