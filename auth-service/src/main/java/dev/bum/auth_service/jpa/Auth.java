package dev.bum.auth_service.jpa;

import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import dev.bum.common.kafka.user.UserDtoForEvent;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;

@Getter
@Entity
@Table(name = "auth")
@NoArgsConstructor
public class Auth {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_blacklisted", nullable = false)
    private Boolean isBlacklisted = false;

    @Column(name = "blacklisted_until")
    private LocalDate blacklistedUntil;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 1L;

    @Builder
    private Auth(Long id, String userId, String password, UserRole role,
                 UserStatus status, Boolean isBlacklisted, LocalDate blacklistedUntil) {
        this.id = id;
        this.userId = normalizeUserId(userId);
        this.password = password;
        this.role = (role != null) ? role : UserRole.ROLE_USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.isBlacklisted = Boolean.TRUE.equals(isBlacklisted);
        this.blacklistedUntil = this.isBlacklisted ? blacklistedUntil : null;
    }

    public void startAfterPreviousAccount(long previousVersion) {
        tokenVersion = Math.max(tokenVersion, Math.addExact(previousVersion, 1L));
    }

    public void updateInfo(UserDtoForEvent event) {
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
        if (!java.util.Objects.equals(previousPassword, password)
                || previousRole != role || previousStatus != status
                || !java.util.Objects.equals(previousBlacklist, isBlacklisted)
                || !java.util.Objects.equals(previousBlacklistUntil, blacklistedUntil)) {
            tokenVersion = Math.addExact(tokenVersion, 1L);
        }
    }

    public boolean isCurrentlyBlacklisted() {
        return Boolean.TRUE.equals(isBlacklisted);
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }
}
