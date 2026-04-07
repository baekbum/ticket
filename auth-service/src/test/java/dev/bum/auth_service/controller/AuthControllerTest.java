package dev.bum.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.dto.TokenDto;
import dev.bum.auth_service.security.SecurityConfig;
import dev.bum.auth_service.service.AuthService;
import dev.bum.auth_service.vo.LoginInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private String apiVersion = "v1";

    @Test
    @DisplayName("로그인 요청 시 성공하면 200 코드 반환.")
    void login_success() throws Exception {
        String userId = "user01";
        String password = "user123!";

        LoginInfo info = new LoginInfo(userId, password);

        given(authService.LoginAndCreateToken(any())).willReturn(new TokenDto("access-token", "refresh-token"));

        mockMvc.perform(post("/api/" + apiVersion + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }
}