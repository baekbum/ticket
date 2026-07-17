package dev.bum.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.controller.user.UserController;
import dev.bum.user_service.security.SecurityConfig;
import dev.bum.user_service.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    private final String baseUrl = "/api/v1";

    @Test
    @DisplayName("인증 없이 내 정보 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("사용자 ID 중복 체크")
    void check_duplication() throws Exception {
        mockMvc.perform(get(baseUrl + "/check/duplication/IU"))
                .andExpect(status().isOk());

        then(userService).should().isDuplicated("IU");
    }

    @Test
    @DisplayName("회원 가입")
    void user_insert() throws Exception {
        InsertUserRequest info = insertUserRequest();

        given(userService.insert(any())).willReturn(userResponse());

        mockMvc.perform(post(baseUrl + "/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("IU"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.grade").value("GENERAL"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));

        then(userService).should().insert(info);
    }

    @Test
    @DisplayName("내 정보 조회")
    void select_my_info() throws Exception {
        given(userService.selectById("IU")).willReturn(userResponse());

        mockMvc.perform(get(baseUrl + "/select/me")
                        .with(authentication(userAuthentication("IU"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"));

        then(userService).should().selectById("IU");
    }

    @Test
    @DisplayName("내 정보 수정")
    void update_my_info() throws Exception {
        UpdateUserRequest info = UpdateUserRequest.builder()
                .email("update@test.com")
                .build();
        UserResponse updated = UserResponse.builder()
                .id(99L)
                .userId("IU")
                .role(UserRole.ROLE_USER)
                .grade(UserGrade.GENERAL)
                .name("IU")
                .phoneNumber("010-0516-0918")
                .email("update@test.com")
                .isBlacklisted(false)
                .build();

        given(userService.update(any(), any())).willReturn(updated);

        mockMvc.perform(put(baseUrl + "/update/me")
                        .with(authentication(userAuthentication("IU")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.email").value("update@test.com"));

        then(userService).should().update("IU", info);
    }

    @Test
    @DisplayName("비밀번호 검증")
    void validate_info() throws Exception {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder()
                .userId("IU")
                .password("IU05160918")
                .build();

        mockMvc.perform(post(baseUrl + "/validate/info")
                        .with(authentication(userAuthentication("IU")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userService).should().validateInfo(info);
    }

    private InsertUserRequest insertUserRequest() {
        return InsertUserRequest.builder()
                .userId("IU")
                .password("IU05160918")
                .name("IU")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .address("Seoul")
                .build();
    }

    private UserResponse userResponse() {
        return UserResponse.builder()
                .id(99L)
                .userId("IU")
                .role(UserRole.ROLE_USER)
                .grade(UserGrade.GENERAL)
                .name("IU")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .isBlacklisted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UsernamePasswordAuthenticationToken userAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
