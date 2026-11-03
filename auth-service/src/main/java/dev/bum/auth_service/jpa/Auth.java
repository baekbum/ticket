package dev.bum.auth_service.jpa;

import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;

import jakarta.persistence.*;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "auth")
@NoArgsConstructor
public class Auth {

    // 고유 식별자.
    @Id
    private Long id;

    // 사용자 ID.
    @Column(nullable = false, unique = true, length = 50)
    private String userId;

    // 비밀번호.
    @Column(nullable = false)
    private String password;

    // 사용자 권한.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // 인증 정보 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // 블랙리스트 등록 여부.
    @Column(name = "is_blacklisted", nullable = false)
    private Boolean isBlacklisted = false;

    // 블랙리스트 적용 종료일.
    @Column(name = "blacklisted_until")
    private LocalDate blacklistedUntil;

    // 토큰 무효화 여부를 판별하는 버전.
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 1L;

    // 비밀번호 초기화 이벤트 버전.
    @Column(name = "password_reset_version", nullable = false)
    private long passwordResetVersion = 0L;

    // 전달받은 값으로 인증 정보 객체를 초기화한다.
    @Builder
    private Auth(
            Long id,
            String userId,
            String password,
            UserRole role,
            UserStatus status,
            Boolean isBlacklisted,
            LocalDate blacklistedUntil
    ) {

        this.id = id;
        this.userId = normalizeUserId(userId);
        this.password = password;
        this.role = (role != null) ? role : UserRole.ROLE_USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.isBlacklisted = Boolean.TRUE.equals(isBlacklisted);
        this.blacklistedUntil = this.isBlacklisted ? blacklistedUntil : null;
    }

    // 인증 정보 처리에서 이전 계정의 상태를 확인한 뒤 새 이벤트 상태를 시작한다.
    public void startAfterPreviousAccount(long previousVersion) {

        tokenVersion = Math.max(tokenVersion, Math.addExact(previousVersion, 1L));
    }

    // 인증 정보를 수정한다.
    public void updateInfo(UserDtoForEvent event) {

        if (event.getPasswordResetVersion() != null) {
            passwordResetVersion = Math.max(passwordResetVersion, event.getPasswordResetVersion());
        }
        String previousPassword = password;
        UserRole previousRole = role;
        UserStatus previousStatus = status;
        Boolean previousBlacklist = isBlacklisted;
        LocalDate previousBlacklistUntil = blacklistedUntil;
        if (StringUtils.hasText(event.getPassword())) {
            this.password = event.getPassword();
        }

        if (StringUtils.hasText(event.getRole())) {
            this.role = UserRole.valueOf(event.getRole());
        }

        if (event.getIsBlacklisted() != null) {
            this.isBlacklisted = event.getIsBlacklisted();
            this.blacklistedUntil = this.isBlacklisted ? event.getBlacklistedUntil() : null;
        }

        if (StringUtils.hasText(event.getStatus())) {
            this.status = UserStatus.valueOf(event.getStatus());
        }

        if (!Objects.equals(previousPassword, password)
                || previousRole != role || previousStatus != status
                || !Objects.equals(previousBlacklist, isBlacklisted)
                || !Objects.equals(previousBlacklistUntil, blacklistedUntil)) {
            tokenVersion = Math.addExact(tokenVersion, 1L);
        }
    }

    // 현재 시점에 블랙리스트가 적용되는지 확인한다.
    public boolean isCurrentlyBlacklisted() {

        return Boolean.TRUE.equals(isBlacklisted);
    }

    // 사용자 ID의 공백과 대소문자를 정규화한다.
    private String normalizeUserId(String userId) {

        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }
}
