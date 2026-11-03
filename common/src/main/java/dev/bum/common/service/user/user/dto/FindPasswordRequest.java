package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindPasswordRequest {

    // 사용자 ID.
    @NotBlank(message = "아이디는 필수 값입니다.")
    private String userId;

    // 이름.
    @NotBlank(message = "이름은 필수 값입니다.")
    private String name;

    // 휴대전화 번호.
    private String phoneNumber;

    // 이메일 주소.
    @Email
    private String email;
}
