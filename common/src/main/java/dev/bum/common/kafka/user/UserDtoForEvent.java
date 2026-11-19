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

    // USER에서 회원별로 증가시킨다. 재발행 시 최초 버전을 유지한다.
    private Long eventVersion;

    // 비밀번호 찾기 재설정마다 별도로 1씩 증가하며 이후 스냅샷에도 유지한다.
    private Long passwordResetVersion;

    // 이벤트 종류.
    private TopicEventType eventType;

    // 고유 식별자.
    private Long id;

    // 사용자 ID.
    private String userId;

    // 비밀번호.
    @ToString.Exclude
    private String password;

    // 비밀번호 찾기를 통한 재설정 완료 이벤트에서만 설정한다.
    private Boolean passwordReset;

    // 사용자 권한.
    private String role;

    // 공연 상태.
    private String status;

    // 블랙리스트 등록 여부.
    private Boolean isBlacklisted;

    // 블랙리스트 적용 종료일.
    private LocalDate blacklistedUntil;
}
