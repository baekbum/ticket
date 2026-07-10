package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

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

    private String role;
}
