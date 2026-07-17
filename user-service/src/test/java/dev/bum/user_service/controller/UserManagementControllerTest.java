package dev.bum.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.user.user.dto.DeleteUserBulkRequest;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserCondRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.controller.user.UserManagementController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(UserManagementController.class)
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    private final String baseUrl = "/api/v1/manage";

    @Test
    @DisplayName("인증 없이 관리자용 사용자 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/IU"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("관리자용 사용자 ID 중복 체크")
    void check_duplication() throws Exception {
        mockMvc.perform(get(baseUrl + "/check/duplication/IU")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk());

        then(userService).should().isDuplicated("IU");
    }

    @Test
    @DisplayName("관리자용 사용자 등록")
    void user_insert() throws Exception {
        InsertUserRequest info = insertUserRequest();

        given(userService.insert(any())).willReturn(userResponse("IU"));

        mockMvc.perform(post(baseUrl + "/insert")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"));

        then(userService).should().insert(info);
    }

    @Test
    @DisplayName("ID로 사용자 조회")
    void select_by_id() throws Exception {
        given(userService.selectById("IU")).willReturn(userResponse("IU"));

        mockMvc.perform(get(baseUrl + "/select/id/IU")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.grade").value("GENERAL"));

        then(userService).should().selectById("IU");
    }

    @Test
    @DisplayName("조건으로 사용자 조회")
    void select_by_cond() throws Exception {
        UserCondRequest cond = UserCondRequest.builder()
                .userId("IU")
                .build();
        CustomPageResponse<UserResponse> expected = CustomPageResponse.of(List.of(userResponse("IU")), 10, 0, 1, 1);

        given(userService.selectByCond(any())).willReturn(expected);

        mockMvc.perform(post(baseUrl + "/select")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("IU"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(userService).should().selectByCond(cond);
    }

    @Test
    @DisplayName("관리자 콘솔 내 정보 조회")
    void select_my_info() throws Exception {
        given(userService.selectById("admin")).willReturn(userResponse("admin"));

        mockMvc.perform(get(baseUrl + "/select/me")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("admin"));

        then(userService).should().selectById("admin");
    }

    @Test
    @DisplayName("관리자 콘솔 내 정보 수정")
    void update_my_info() throws Exception {
        UpdateUserRequest info = UpdateUserRequest.builder()
                .email("admin@test.com")
                .build();
        UserResponse updated = userResponse("admin");

        given(userService.update(any(), any())).willReturn(updated);

        mockMvc.perform(put(baseUrl + "/update/me")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("admin"));

        then(userService).should().update("admin", info);
    }

    @Test
    @DisplayName("관리자 콘솔 비밀번호 검증")
    void validate_info() throws Exception {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder()
                .userId("admin")
                .password("admin1234")
                .build();

        mockMvc.perform(post(baseUrl + "/validate/info")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userService).should().validateInfo(info);
    }

    @Test
    @DisplayName("사용자 정보 수정")
    void update_by_id() throws Exception {
        UpdateUserRequest info = UpdateUserRequest.builder()
                .phoneNumber("010-3333-4444")
                .email("update@test.com")
                .isBlacklisted(true)
                .build();

        given(userService.update(any(), any())).willReturn(userResponse("IU"));

        mockMvc.perform(put(baseUrl + "/update/id/IU")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"));

        then(userService).should().update("IU", info);
    }

    @Test
    @DisplayName("사용자 비밀번호 초기화")
    void init_password() throws Exception {
        mockMvc.perform(put(baseUrl + "/init/password/IU")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk());

        then(userService).should().initPassword("IU");
    }

    @Test
    @DisplayName("사용자 삭제")
    void delete_by_id() throws Exception {
        given(userService.delete("IU")).willReturn(userResponse("IU"));

        mockMvc.perform(delete(baseUrl + "/delete/id/IU")
                        .with(authentication(adminAuthentication("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"));

        then(userService).should().delete("IU");
    }

    @Test
    @DisplayName("사용자 일괄 삭제")
    void delete_bulk() throws Exception {
        DeleteUserBulkRequest info = DeleteUserBulkRequest.builder()
                .userIds(List.of("user01", "user02"))
                .build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .with(authentication(adminAuthentication("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userService).should().deleteBulk(info);
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

    private UserResponse userResponse(String userId) {
        return UserResponse.builder()
                .id(99L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .grade(UserGrade.GENERAL)
                .name(userId)
                .phoneNumber("010-0516-0918")
                .email(userId + "@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .isBlacklisted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UsernamePasswordAuthenticationToken adminAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
