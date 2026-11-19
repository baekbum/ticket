package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertUserRequest {

    // 사용자 ID.
    @NotBlank(message = "유저 ID는 필수 값입니다.")
    private String userId;

    // 비밀번호.
    @NotBlank(message = "비밀번호는 필수 값입니다.")
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    @ToString.Exclude
    private String password;

    // 이름.
    @NotBlank(message = "유저 이름은 필수 값입니다.")
    @Size(min = 2, message = "이름은 두 글자 이상입니다.")
    private String name;

    // 휴대전화 번호.
    @NotBlank(message = "유저 핸드폰 번호는 필수 값입니다.")
    private String phoneNumber;

    // 이메일 주소.
    @Email
    @NotBlank(message = "이메일은 필수 값입니다.")
    private String email;

    // 생년월일.
    private LocalDate birthDate;

    // 주소.
    private String address;
}
