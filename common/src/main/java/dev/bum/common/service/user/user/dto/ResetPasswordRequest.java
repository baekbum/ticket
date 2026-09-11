package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "비밀번호 재설정 토큰은 필수 값입니다.")
    private String resetToken;

    @NotBlank(message = "비밀번호는 필수 값입니다.")
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    private String password;
}
