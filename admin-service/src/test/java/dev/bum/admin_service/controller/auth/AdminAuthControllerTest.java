package dev.bum.admin_service.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.auth.AuthServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.auth.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminAuthController.class)
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @Test
    @DisplayName("관리자 로그인")
    void auth_login() throws Exception {
        LoginRequest info = new LoginRequest("admin", "password");
        TokenResponse response = new TokenResponse("access-token", "refresh-token");

        given(authServiceClient.login(info)).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        then(authServiceClient).should().login(info);
    }
}
