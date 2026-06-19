package dev.bum.user_service.jpa;

import dev.bum.common.service.user.dto.UserDto;
import dev.bum.common.service.user.enums.UserRole;
import dev.bum.common.service.user.vo.InsertUserInfo;
import dev.bum.common.service.user.vo.UpdateUserInfo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_user_id", columnList = "user_id"),
        @Index(name = "idx_users_email", columnList = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // 기본값 처리는 비즈니스 로직이나 @Builder.Default 활용

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_blacklisted")
    private Boolean isBlacklisted;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * InsertInfo -> Entity
     */
    public User(InsertUserInfo info) {
        this.userId = info.getUserId();
        this.password = info.getPassword();
        this.role = UserRole.ROLE_USER;
        this.name = info.getName();
        this.phoneNumber = info.getPhoneNumber();
        this.email = info.getEmail();

        if (info.getBirthDate() != null) {
            this.birthDate = info.getBirthDate();
        }

        if (StringUtils.hasText(info.getAddress())) {
            this.address = info.getAddress();
        }

        this.isBlacklisted = false;
    }

    /**
     * 사용자 정보 수정
     */
    public void updateInfo(UpdateUserInfo info) {
        if (StringUtils.hasText(info.getPassword())) {
            this.password = info.getPassword();
        }

        if (StringUtils.hasText(info.getPhoneNumber())) {
            this.phoneNumber = info.getPhoneNumber();
        }

        if (StringUtils.hasText(info.getEmail())) {
            this.email = info.getEmail();
        }

        if (info.getBirthDate() != null) {
            this.birthDate = info.getBirthDate();
        }

        if (StringUtils.hasText(info.getAddress())) {
            this.address = info.getAddress();
        }

        if (info.getIsBlacklisted() != null) {
            this.isBlacklisted = info.getIsBlacklisted();
        }
    }

    /**
     * Entity -> 공통 모듈 UserDto 변환 메서드
     * 패키지가 서로 다른 Enum(UserRole) 간의 동치 매핑 처리를 포함합니다.
     */
    public UserDto toDto() {
        return UserDto.builder()
                .id(this.id)
                .userId(this.userId)
                .role(this.role != null ? dev.bum.common.service.user.enums.UserRole.valueOf(this.role.name()) : null)
                .name(this.name)
                .phoneNumber(this.phoneNumber)
                .email(this.email)
                .birthDate(this.birthDate)
                .address(this.address)
                .isBlacklisted(this.isBlacklisted)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}