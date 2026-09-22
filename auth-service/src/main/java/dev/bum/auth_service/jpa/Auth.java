package dev.bum.auth_service.jpa;

import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import dev.bum.common.kafka.user.UserDtoForEvent;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

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

    @Builder
    public Auth(Long id, String userId, String password, UserRole role) {
        this.id = id;
        this.userId = normalizeUserId(userId);
        this.password = password;
        this.role = (role != null) ? role : UserRole.ROLE_USER;
        this.status = UserStatus.ACTIVE;
    }

    @Builder
    public Auth(UserDtoForEvent event) {
        this.id = event.getId();
        this.userId = normalizeUserId(event.getUserId());
        this.password = event.getPassword();
        this.role = UserRole.valueOf(event.getRole());
        this.status = event.getStatus() == null ? UserStatus.ACTIVE : UserStatus.valueOf(event.getStatus());
    }

    public void updateInfo(UserDtoForEvent event) {
        if (StringUtils.hasText(event.getPassword())) {
            this.password = event.getPassword();
        }

        if (StringUtils.hasText(event.getRole())) {
            this.role = UserRole.valueOf(event.getRole());
        }

        if (StringUtils.hasText(event.getStatus())) {
            this.status = UserStatus.valueOf(event.getStatus());
        }
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }
}
