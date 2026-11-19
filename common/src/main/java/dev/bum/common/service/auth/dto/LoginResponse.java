package dev.bum.common.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    // 처리 성공 여부.
    private boolean success;

    // 처리 결과 메시지.
    private String message;

    // 이름.
    private String name;

    // 접근 인증 토큰.
    private String accessToken;

    // 접근 토큰 재발급용 토큰.
    private String refreshToken;
}
