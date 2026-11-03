package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class ChangeMyPasswordRequest {

    // 현재 비밀번호.
    @NotBlank
    @ToString.Exclude
    private String currentPassword;

    // 변경할 비밀번호.
    @NotBlank
    @Size(min = 8, message = "비밀번호는 최소 여덟 글자 이상입니다.")
    @ToString.Exclude
    private String newPassword;

    // 새 비밀번호 확인 값.
    @NotBlank
    @ToString.Exclude
    private String newPasswordConfirm;
}
