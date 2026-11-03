package dev.bum.auth_service.jpa;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "user_login_locks")
@NoArgsConstructor
public class UserLoginLock {

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 인증 정보 식별자.
    @Column(name = "auth_id", nullable = false, unique = true)
    private Long authId;

    // 연속 로그인 실패 횟수.
    @Column(nullable = false)
    private int failedAttempts;

    // 마지막 실패 시각.
    private LocalDateTime lastFailedAt;

    // 로그인 잠금 시작 시각.
    private LocalDateTime lockedAt;

    // 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 전달받은 값으로 사용자 로그인 잠금 객체를 초기화한다.
    @Builder
    private UserLoginLock(Long authId) {

        this.authId = authId;
    }

    // 현재 로그인 잠금 상태인지 확인한다.
    public boolean isLocked() {

        return lockedAt != null;
    }

    // 실패 사유와 재시도 정보를 기록한다.
    public void recordFailure() {

        if (isLocked()) {
            return;
        }

        failedAttempts++;
        lastFailedAt = LocalDateTime.now();

        if (failedAttempts >= 5) {
            lockedAt = lastFailedAt;
        }
    }

    // 잠금 해제는 비밀번호 찾기를 통한 재설정 완료 시에만 호출한다.
    public void reset() {

        failedAttempts = 0;
        lastFailedAt = null;
        lockedAt = null;
    }

    // 성공 결과를 기록한다.
    public void recordSuccess() {

        if (isLocked()) {
            throw new IllegalStateException("잠긴 계정의 로그인 실패 횟수를 초기화할 수 없습니다.");
        }

        failedAttempts = 0;
        lastFailedAt = null;
    }

    // 새 사용자 생성 이벤트를 인증 정보에 반영한다.
    @PrePersist
    private void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    // 사용자 변경 이벤트를 인증 정보에 반영한다.
    @PreUpdate
    private void onUpdate() {

        updatedAt = LocalDateTime.now();
    }
}
