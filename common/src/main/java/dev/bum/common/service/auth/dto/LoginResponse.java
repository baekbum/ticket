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

    private boolean success;
    private String message;
    private String name;
    private String accessToken;
    private String refreshToken;
}
