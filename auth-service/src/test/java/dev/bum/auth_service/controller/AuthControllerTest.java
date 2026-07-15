package dev.bum.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.auth_service.security.SecurityConfig;
import dev.bum.auth_service.service.AuthService;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.service.auth.dto.LoginRequest;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider tokenProvider;

    private final String apiVersion = "v1";

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void login_success() throws Exception {
        LoginRequest info = new LoginRequest("user01", "user123!");
        TokenResponse tokens = new TokenResponse("access-token", "refresh-token");

        given(authService.LoginAndCreateToken(any())).willReturn(tokens);

        mockMvc.perform(post("/api/" + apiVersion + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        then(authService).should().LoginAndCreateToken(info);
    }

    @Test
    @DisplayName("토큰 검증 성공 시 사용자 헤더 반환")
    void validate_token_success() throws Exception {
        String token = "access-token";

        given(tokenProvider.validateToken(token)).willReturn(true);
        given(tokenProvider.getUserId(token)).willReturn("user01");
        given(tokenProvider.getRole(token)).willReturn("ROLE_USER");

        mockMvc.perform(get("/api/" + apiVersion + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", "user01"))
                .andExpect(header().string("X-User-Role", "ROLE_USER"));

        then(tokenProvider).should().validateToken(token);
        then(tokenProvider).should().getUserId(token);
        then(tokenProvider).should().getRole(token);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401 응답")
    void validate_token_without_authorization_header() throws Exception {
        mockMvc.perform(get("/api/" + apiVersion + "/validate"))
                .andExpect(status().isUnauthorized());

        then(tokenProvider).should(never()).validateToken(any());
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 401 응답")
    void validate_token_with_invalid_header_format() throws Exception {
        mockMvc.perform(get("/api/" + apiVersion + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, "access-token"))
                .andExpect(status().isUnauthorized());

        then(tokenProvider).should(never()).validateToken(any());
    }

    @Test
    @DisplayName("잘못된 토큰이면 403 응답")
    void validate_token_with_invalid_token() throws Exception {
        String token = "invalid-token";

        given(tokenProvider.validateToken(token)).willThrow(new MalformedJwtException("invalid token"));

        mockMvc.perform(get("/api/" + apiVersion + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        then(tokenProvider).should().validateToken(token);
    }

    @Test
    @DisplayName("토큰 재발급 성공 시 새 토큰 반환")
    void reissue_success() throws Exception {
        String refreshToken = "refresh-token";
        TokenResponse tokens = new TokenResponse("new-access-token", "new-refresh-token");

        given(authService.reissueToken(refreshToken)).willReturn(tokens);

        mockMvc.perform(post("/api/" + apiVersion + "/reissue")
                        .header("Authorization-Refresh", "Bearer " + refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

        then(authService).should().reissueToken(refreshToken);
    }

    @Test
    @DisplayName("Refresh 토큰 헤더 형식이 잘못되면 400 응답")
    void reissue_with_invalid_header_format() throws Exception {
        mockMvc.perform(post("/api/" + apiVersion + "/reissue")
                        .header("Authorization-Refresh", "refresh-token"))
                .andExpect(status().isBadRequest());

        then(authService).should(never()).reissueToken(any());
    }
}
