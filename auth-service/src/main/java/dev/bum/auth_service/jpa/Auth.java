package dev.bum.auth_service.jpa;

import dev.bum.auth_service.enums.UserRole;
import dev.bum.common.kafka.user.UserDtoForEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@Entity
@Table(name = "auth")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Builder
    public Auth(Long id, String userId, String password, UserRole role) {
        this.id = id;
        this.userId = userId;
        this.password = password;
        this.role = (role != null) ? role : UserRole.ROLE_USER;
    }

    @Builder
    public Auth(UserDtoForEvent event) {
        this.id = event.getId();
        this.userId = event.getUserId();
        this.password = event.getPassword();
        this.role = UserRole.valueOf(event.getRole());
    }

    public void updateInfo(UserDtoForEvent event) {
        if (StringUtils.hasText(event.getPassword())) {
            this.password = event.getPassword();
        }

        if (StringUtils.hasText(event.getRole())) {
            this.role = UserRole.valueOf(event.getRole());
        }
    }
}
