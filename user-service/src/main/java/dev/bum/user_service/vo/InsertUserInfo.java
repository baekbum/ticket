package dev.bum.user_service.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertUserInfo {

    @NotBlank(message = "유저 ID는 필수 값입니다.")
    private String userId;

    @NotBlank(message = "비밀번호는 필수 값입니다.")
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    private String password;

    @NotBlank(message = "유저 이름은 필수 값입니다.")
    @Size(min = 2, message = "이름은 두 글자 이상입니다.")
    private String name;

    @NotBlank(message = "유저 핸드폰 번호는 필수 값입니다.")
    private String phoneNumber;

    @Email
    @NotBlank(message = "이메일은 필수 값입니다.")
    private String email;

    private LocalDate birthDate;

    private String address;
}
