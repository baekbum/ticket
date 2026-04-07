package dev.bum.auth_service.controller;

import dev.bum.common.dto.TokenDto;
import dev.bum.auth_service.service.AuthService;
import dev.bum.auth_service.vo.LoginInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenDto> login(@Valid @RequestBody LoginInfo info) {
        return ResponseEntity.ok(authService.LoginAndCreateToken(info));
    }
}
