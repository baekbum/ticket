package dev.bum.common.service.user.user.dto;

import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    // 고유 식별자.
    private Long id;

    // 사용자 ID.
    private String userId;

    // 사용자 권한.
    private UserRole role;

    // 회원 등급.
    private UserGrade grade;

    // 이름.
    private String name;

    // 휴대전화 번호.
    private String phoneNumber;

    // 이메일 주소.
    private String email;

    // 생년월일.
    private LocalDate birthDate;

    // 주소.
    private String address;

    // 블랙리스트 등록 여부.
    private Boolean isBlacklisted;

    // 블랙리스트 적용 종료일.
    private LocalDate blacklistedUntil;

    // 사용자 상태.
    private UserStatus status;

    // 회원 탈퇴 시각.
    private LocalDateTime withdrawAt;

    // 생성 시각.
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    private LocalDateTime updatedAt;
}
