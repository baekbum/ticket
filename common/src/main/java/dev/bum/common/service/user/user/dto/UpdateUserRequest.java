package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import dev.bum.common.service.user.user.enums.UserStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserRequest {

    // 비밀번호.
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    @ToString.Exclude
    private String password;

    // 휴대전화 번호.
    private String phoneNumber;

    // 이메일 주소.
    @Email
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

    // 사용자 권한.
    private String role;

    // 회원 등급.
    private String grade;
}
