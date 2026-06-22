package dev.bum.admin_service.controller.auth;

import dev.bum.admin_service.feign.auth.AuthServiceClient;

import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.service.auth.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AuthServiceClient authServiceClient;

    /**
     * 로그인 시도
     * @param info
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest info) {
        return ResponseEntity.ok(authServiceClient.login(info));
    }
}
