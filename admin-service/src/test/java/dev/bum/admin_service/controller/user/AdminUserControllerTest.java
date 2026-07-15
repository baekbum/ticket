package dev.bum.admin_service.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.user.UserServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.user.user.dto.DeleteUserBulkRequest;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserCondRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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
@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserServiceClient userServiceClient;

    private final String baseUrl = "/api/v1/user";

    @Test
    @DisplayName("인증 정보 없이 사용자 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/user01"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 등록")
    void user_insert() throws Exception {
        InsertUserRequest info = insertRequest();
        UserResponse response = userResponse("user01");

        given(userServiceClient.insert(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userServiceClient).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 사용자 조회")
    void user_select_by_id() throws Exception {
        given(userServiceClient.selectById("user01")).willReturn(userResponse("user01"));

        mockMvc.perform(get(baseUrl + "/select/id/user01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userServiceClient).should().selectById("user01");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 사용자 조회")
    void user_select_by_cond() throws Exception {
        UserCondRequest cond = UserCondRequest.builder().userId("user01").build();
        CustomPageResponse<UserResponse> response = CustomPageResponse.of(List.of(userResponse("user01")), 10, 0, 1, 1);

        given(userServiceClient.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("user01"));

        then(userServiceClient).should().selectByCond(cond);
    }

    @Test
    @DisplayName("내 정보 조회")
    void user_select_my_info() throws Exception {
        given(userServiceClient.selectById("user01")).willReturn(userResponse("user01"));

        mockMvc.perform(get(baseUrl + "/select/me")
                        .with(authentication(adminAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userServiceClient).should().selectById("user01");
    }

    @Test
    @DisplayName("내 정보 수정")
    void user_update_my_info() throws Exception {
        UpdateUserRequest info = updateRequest();
        given(userServiceClient.update("user01", info)).willReturn(userResponse("user01"));

        mockMvc.perform(put(baseUrl + "/update/me")
                        .with(authentication(adminAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userServiceClient).should().update("user01", info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("비밀번호 검증")
    void user_validate_info() throws Exception {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder().userId("user01").password("password").build();

        mockMvc.perform(post(baseUrl + "/validate/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userServiceClient).should().validateInfo(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 정보 수정")
    void user_update() throws Exception {
        UpdateUserRequest info = updateRequest();
        given(userServiceClient.update("user01", info)).willReturn(userResponse("user01"));

        mockMvc.perform(put(baseUrl + "/update/id/user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userServiceClient).should().update("user01", info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 비밀번호 초기화")
    void user_init_password() throws Exception {
        mockMvc.perform(put(baseUrl + "/init/password/user01"))
                .andExpect(status().isOk());

        then(userServiceClient).should().initPassword("user01");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 삭제")
    void user_delete() throws Exception {
        given(userServiceClient.delete("user01")).willReturn(userResponse("user01"));

        mockMvc.perform(delete(baseUrl + "/delete/id/user01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userServiceClient).should().delete("user01");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 벌크 삭제")
    void user_delete_bulk() throws Exception {
        DeleteUserBulkRequest info = DeleteUserBulkRequest.builder().userIds(List.of("user01", "user02")).build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userServiceClient).should().deleteBulk(info);
    }

    private InsertUserRequest insertRequest() {
        return InsertUserRequest.builder()
                .userId("user01")
                .password("password1")
                .name("User")
                .phoneNumber("01012345678")
                .email("user01@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("Seoul")
                .build();
    }

    private UpdateUserRequest updateRequest() {
        return UpdateUserRequest.builder()
                .email("updated@test.com")
                .address("Seoul")
                .build();
    }

    private UserResponse userResponse(String userId) {
        return UserResponse.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .name("User")
                .email("user01@test.com")
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
