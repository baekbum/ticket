package dev.bum.common.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    // 사용자 ID.
    private String userId;

    // 비밀번호.
    @ToString.Exclude
    private String password;
}
