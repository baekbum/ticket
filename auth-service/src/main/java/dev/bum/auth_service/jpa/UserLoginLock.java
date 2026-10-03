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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_id", nullable = false, unique = true)
    private Long authId;

    @Column(nullable = false)
    private int failedAttempts;

    private LocalDateTime lastFailedAt;
    private LocalDateTime lockedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserLoginLock(Long authId) {
        this.authId = authId;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

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

    /** 잠금 해제는 비밀번호 찾기를 통한 재설정 완료 시에만 호출한다. */
    public void reset() {
        failedAttempts = 0;
        lastFailedAt = null;
        lockedAt = null;
    }

    public void recordSuccess() {
        if (isLocked()) {
            throw new IllegalStateException("잠긴 계정의 로그인 실패 횟수를 초기화할 수 없습니다.");
        }

        failedAttempts = 0;
        lastFailedAt = null;
    }

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
