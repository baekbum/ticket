package dev.bum.common.jwt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    // 접근 인증 토큰.
    private String accessToken;

    // 접근 토큰 재발급용 토큰.
    private String refreshToken;
}
