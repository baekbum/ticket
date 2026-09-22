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

    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    private String password;

    private String phoneNumber;

    @Email
    private String email;

    private LocalDate birthDate;

    private String address;

    private Boolean isBlacklisted;
    private LocalDateTime blacklistedUntil;

    private UserStatus status;
    private LocalDateTime withdrawAt;

    private String role;

    private String grade;
}
