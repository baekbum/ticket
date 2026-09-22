package dev.bum.user_service.jpa.user;

import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_user_id", columnList = "user_id"),
        @Index(name = "idx_users_email", columnList = "email")
})
@Getter
@NoArgsConstructor
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserGrade grade = UserGrade.GENERAL;

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

    @Column(name = "blacklisted_until")
    private LocalDateTime blacklistedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "withdraw_at")
    private LocalDateTime withdrawAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * InsertInfo -> Entity
     */
    public User(InsertUserRequest info) {
        this.userId = normalizeUserId(info.getUserId());
        this.password = info.getPassword();
        this.role = UserRole.ROLE_USER;
        this.grade = UserGrade.GENERAL;
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
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 사용자 정보 수정
     */
    public void updateInfo(UpdateUserRequest info) {
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
            this.blacklistedUntil = info.getIsBlacklisted() ? info.getBlacklistedUntil() : null;
        }

        if (info.getBlacklistedUntil() != null && !Boolean.FALSE.equals(info.getIsBlacklisted())) {
            this.blacklistedUntil = info.getBlacklistedUntil();
            this.isBlacklisted = true;
        }

        if (StringUtils.hasText(info.getRole())) {
            this.role = UserRole.valueOf(info.getRole());
        }

        if (StringUtils.hasText(info.getGrade())) {
            this.grade = UserGrade.valueOf(info.getGrade());
        }

        if (info.getStatus() == UserStatus.WITHDRAWN) {
            this.status = UserStatus.WITHDRAWN;
            this.withdrawAt = info.getWithdrawAt() != null ? info.getWithdrawAt()
                    : this.withdrawAt != null ? this.withdrawAt : LocalDateTime.now();
        } else if (info.getStatus() == UserStatus.ACTIVE) {
            this.status = UserStatus.ACTIVE;
            this.withdrawAt = null;
        }
    }

    public void withdraw(LocalDateTime now) {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawAt = now;
    }

    public UserResponse toResponse() {
        return UserResponse.builder()
                .id(this.id)
                .userId(this.userId)
                .role(this.role != null ? UserRole.valueOf(this.role.name()) : null)
                .grade(this.grade != null ? UserGrade.valueOf(this.grade.name()) : null)
                .name(this.name)
                .phoneNumber(this.phoneNumber)
                .email(this.email)
                .birthDate(this.birthDate)
                .address(this.address)
                .isBlacklisted(this.isBlacklisted)
                .blacklistedUntil(this.blacklistedUntil)
                .status(this.status)
                .withdrawAt(this.withdrawAt)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }
}
