package dev.bum.auth_service.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users") // 실제 DB 테이블 이름과 매핑
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // DB 관리용 PK는 반드시 있어야 합니다.

    @Column(nullable = false, unique = true, length = 50)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Builder
    public Auth(String userId, String password, String role) {
        this.userId = userId;
        this.password = password;
        this.role = (role != null) ? role : "ROLE_USER";
    }
}
