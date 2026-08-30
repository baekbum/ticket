package dev.bum.client_api_service.controller.auth;

import dev.bum.client_api_service.feign.auth.AuthServiceClient;
import dev.bum.client_api_service.feign.user.UserServiceClient;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.service.auth.dto.LoginResponse;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ClientAuthController {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            TokenResponse tokenResponse = authServiceClient.login(request);
            UserResponse userResponse = userServiceClient.selectMyInfo("Bearer " + tokenResponse.getAccessToken());

            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("로그인되었습니다.")
                    .name(userResponse.getName())
                    .accessToken(tokenResponse.getAccessToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .build());
        } catch (FeignException.BadRequest e) {
            return ResponseEntity.ok(LoginResponse.builder()
                    .success(false)
                    .message("정보가 올바르지 않습니다.")
                    .build());
        }
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestHeader("Authorization-Refresh") String refreshHeader) {
        return ResponseEntity.ok(authServiceClient.reissue(refreshHeader));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization-Refresh") String refreshHeader) {
        authServiceClient.logout(refreshHeader);
        return ResponseEntity.noContent().build();
    }
}
