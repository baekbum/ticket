package dev.bum.auth_service.controller;

import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.auth_service.service.AuthService;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest info) {
        return ResponseEntity.ok(authService.LoginAndCreateToken(info));
    }

    /**
     * Nginx auth_request 전용 검증 엔드포인트
     * (SecurityConfig에서 permitAll로 열려있고, Nginx internal;로 보호되는 경로)
     */
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {

        // 1. 헤더 자체가 없거나 Bearer 형식이 아니면 즉시 인증 실패 처리
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[Nginx Auth] Authorization 헤더가 누락되었거나 형식이 잘못되었습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 401 반환 -> Nginx가 튕겨냄
        }

        String token = authHeader.substring(7);

        try {
            // 2. JwtTokenProvider를 통해 토큰 검증
            // 💡 이제 validateToken이 내부에서 예외를 던지므로(throw), try-catch로 잡아냅니다!
            tokenProvider.validateToken(token);

            // 3. 토큰이 완벽히 유효하다면 내부에서 클레임(ID, Role) 추출
            String userId = tokenProvider.getUserId(token);
            String role = tokenProvider.getRole(token);

            // Nginx가 뒷단 서비스(user, ticket 등)로 포워딩할 수 있도록 헤더에 꽂아서 200 OK 리턴
            return ResponseEntity.ok()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .build();

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("[Nginx Auth] 만료된 토큰 접근 발생 -> Nginx에 401 전송");
            // 🌟 중요: 만료 시 401을 줘야 Nginx가 클라이언트에게 401을 그대로 전달하고,
            // 그걸 본 대시보드 Axios/Fetch 인터셉터가 reissue(재발급)를 요청하게 됩니다!
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            log.warn("[Nginx Auth] 변조되거나 잘못된 토큰 비정상 접근 발생 -> Nginx에 403 전송: {}", e.getMessage());
            // 변조된 토큰은 갱신 기회를 주지 않고 완전히 차단해야 하므로 403을 줍니다.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * 클라이언트가 직접 호출하는 토큰 재발급(갱신) 엔드포인트
     */
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestHeader("Authorization-Refresh") String refreshHeader) {
        if (refreshHeader == null || !refreshHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String refreshToken = refreshHeader.substring(7);
        TokenResponse tokenResponse = authService.reissueToken(refreshToken);

        return ResponseEntity.ok(tokenResponse);
    }
}
