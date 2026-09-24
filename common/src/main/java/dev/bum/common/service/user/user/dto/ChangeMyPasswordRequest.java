package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeMyPasswordRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    private String newPassword;

    @NotBlank
    private String newPasswordConfirm;
}
