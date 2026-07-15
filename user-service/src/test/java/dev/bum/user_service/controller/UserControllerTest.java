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
import org.springframework.security.test.context.support.WithMockUser;
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

    private final String apiVersion = "v1";

    private final UserResponse iu = UserResponse.builder()
            .id(99L)
            .userId("IU")
            .role(UserRole.ROLE_USER)
            .name("IU")
            .phoneNumber("010-0516-0918")
            .email("IU@test.com")
            .birthDate(LocalDate.of(1993, 5, 16))
            .isBlacklisted(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    @Test
    @DisplayName("인증 정보 없이 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        UserCondRequest cond = UserCondRequest.builder()
                .page(0)
                .size(10)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 등록")
    void user_insert() throws Exception {
        InsertUserRequest userInfo = InsertUserRequest.builder()
                .userId("IU")
                .password("IU05160918")
                .name("IU")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .address("Seoul")
                .build();

        given(userService.insert(any())).willReturn(iu);

        mockMvc.perform(post("/api/" + apiVersion + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("IU"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));

        then(userService).should().insert(userInfo);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("빈 조건으로 전체 유저 조회")
    void select_all() throws Exception {
        List<UserResponse> userList = List.of(
                UserResponse.builder().userId("user01").name("user01").build(),
                UserResponse.builder().userId("user02").name("user02").build()
        );
        UserCondRequest cond = UserCondRequest.builder().build();
        CustomPageResponse<UserResponse> expectedResult = CustomPageResponse.of(userList, 10, 0, 2, 1);

        given(userService.selectByCond(any())).willReturn(expectedResult);

        mockMvc.perform(post("/api/" + apiVersion + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("user01"))
                .andExpect(jsonPath("$.content[1].userId").value("user02"))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        then(userService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 유저 조회")
    void select_by_id() throws Exception {
        given(userService.selectById(any())).willReturn(iu);

        mockMvc.perform(get("/api/" + apiVersion + "/select/id/IU")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("IU"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));

        then(userService).should().selectById("IU");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 유저 조회")
    void select_by_cond() throws Exception {
        UserCondRequest cond = UserCondRequest.builder()
                .userId("IU")
                .build();
        CustomPageResponse<UserResponse> expectedResult = CustomPageResponse.of(List.of(iu), 10, 0, 1, 1);

        given(userService.selectByCond(any())).willReturn(expectedResult);

        mockMvc.perform(post("/api/" + apiVersion + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value("IU"))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(userService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 정보 수정")
    void updateWithInfo() throws Exception {
        String userId = "update";
        UpdateUserRequest info = UpdateUserRequest.builder()
                .phoneNumber("010-3333-4444")
                .email("update@test.com")
                .isBlacklisted(true)
                .build();
        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .name("update")
                .phoneNumber("010-3333-4444")
                .email("update@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .isBlacklisted(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(userService.update(any(), any())).willReturn(userResponse);

        mockMvc.perform(put("/api/" + apiVersion + "/update/id/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("update"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("update@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-3333-4444"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(true));

        then(userService).should().update(userId, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 삭제")
    void deleteById() throws Exception {
        given(userService.delete(any())).willReturn(iu);

        mockMvc.perform(delete("/api/" + apiVersion + "/delete/id/IU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("IU"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));

        then(userService).should().delete("IU");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 ID 중복 체크")
    void check_duplication() throws Exception {
        mockMvc.perform(get("/api/" + apiVersion + "/check/duplication/IU"))
                .andExpect(status().isOk());

        then(userService).should().isDuplicated("IU");
    }

    @Test
    @DisplayName("내 정보 조회")
    void select_my_info() throws Exception {
        given(userService.selectById("IU")).willReturn(iu);

        mockMvc.perform(get("/api/" + apiVersion + "/select/me")
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
                .name("IU")
                .phoneNumber("010-0516-0918")
                .email("update@test.com")
                .isBlacklisted(false)
                .build();

        given(userService.update(any(), any())).willReturn(updated);

        mockMvc.perform(put("/api/" + apiVersion + "/update/me")
                        .with(authentication(userAuthentication("IU")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.email").value("update@test.com"));

        then(userService).should().update("IU", info);
    }

    @WithMockUser(username = "IU", roles = {"USER"})
    @Test
    @DisplayName("비밀번호 검증")
    void validate_info() throws Exception {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder()
                .userId("IU")
                .password("IU05160918")
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/validate/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userService).should().validateInfo(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("비밀번호 초기화")
    void init_password() throws Exception {
        mockMvc.perform(put("/api/" + apiVersion + "/init/password/IU"))
                .andExpect(status().isOk());

        then(userService).should().initPassword("IU");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 벌크 삭제")
    void delete_bulk() throws Exception {
        DeleteUserBulkRequest info = DeleteUserBulkRequest.builder()
                .userIds(List.of("user01", "user02"))
                .build();

        mockMvc.perform(delete("/api/" + apiVersion + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(userService).should().deleteBulk(info);
    }

    private UsernamePasswordAuthenticationToken userAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
