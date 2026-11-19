package dev.bum.user_service.jpa.user;

import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.enums.UserStatus;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 ID.
    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    // 비밀번호.
    @Column(nullable = false, length = 255)
    private String password;

    // 사용자 권한.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // 기본값 처리는 비즈니스 로직이나 @Builder.Default 활용

    // 회원 등급.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserGrade grade = UserGrade.GENERAL;

    // 이름.
    @Column(nullable = false, length = 20)
    private String name;

    // 휴대전화 번호.
    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    // 이메일 주소.
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // 생년월일.
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // 주소.
    @Column(columnDefinition = "TEXT")
    private String address;

    // 블랙리스트 등록 여부.
    @Column(name = "is_blacklisted")
    private Boolean isBlacklisted;

    // 블랙리스트 적용 종료일.
    @Column(name = "blacklisted_until")
    private LocalDate blacklistedUntil;

    // 사용자 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // 회원 탈퇴 시각.
    @Column(name = "withdraw_at")
    private LocalDateTime withdrawAt;

    // 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 사용자 정보 수정
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

    // 사용자 처리에서 회원을 탈퇴 상태로 변경한다.
    public void withdraw(LocalDateTime now) {

        this.status = UserStatus.WITHDRAWN;
        this.withdrawAt = now;
    }

    // 외부 응답에 사용할 DTO로 변환한다.
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
}
