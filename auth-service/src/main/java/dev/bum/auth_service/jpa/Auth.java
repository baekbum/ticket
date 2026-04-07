package dev.bum.auth_service.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Builder
    public Auth(Long id, String userId, String password, String role) {
        this.id = id;
        this.userId = userId;
        this.password = password;
        this.role = (role != null) ? role : "ROLE_USER";
    }
}
