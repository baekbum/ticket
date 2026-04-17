package dev.bum.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.user_service.dto.UserDto;
import dev.bum.user_service.enums.UserRole;
import dev.bum.user_service.security.JwtAuthenticationFilter;
import dev.bum.user_service.security.SecurityConfig;
import dev.bum.user_service.service.UserService;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private String apiVersion = "v1";

    private final UserDto IU = UserDto.builder()
            .id(99L)
            .userId("IU")
            .role(UserRole.ROLE_USER)
            .name("아이유")
            .phoneNumber("010-0516-0918")
            .email("IU@test.com")
            .birthDate(LocalDate.of(1993, 5, 16))
            .isBlacklisted(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    @Test
    @DisplayName("토큰 값 오류")
    void token_invalid() throws Exception {
        UserCond cond = UserCond.builder()
                .page(0)
                .size(10)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/selectAll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 등록 성공하면 200 코드 반환 및 등록된 유저 DTO 반환")
    void user_insert() throws Exception {

        InsertUserInfo userInfo = InsertUserInfo.builder()
                .userId("IU")
                .password("IU05160918")
                .name("아이유")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16)) // LocalDate 타입에 맞춰 생성
                .address("서울시 용산구 한남동")
                .build();

        given(userService.insert(any())).willReturn(IU);

        mockMvc.perform(post("/api/" + apiVersion + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("아이유"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("전체 유저 조회")
    void select_all() throws Exception {
        UserDto user01 = UserDto.builder()
                .userId("user01")
                .name("유저1번")
                .build();

        UserDto user02 = UserDto.builder()
                .userId("user02")
                .name("유저2번")
                .build();

        List<UserDto> userList = List.of(user01, user02);

        UserCond cond = UserCond.builder().build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<UserDto> userPage = new PageImpl<>(userList, pageable, userList.size());

        given(userService.selectAll(any())).willReturn(userPage);

        mockMvc.perform(post("/api/" + apiVersion + "/selectAll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                // PagedModel 구조에 맞게 검증 (content 필드 안에 리스트가 들어감)
                .andExpect(jsonPath("$.content[0].userId").value("user01"))
                .andExpect(jsonPath("$.content[1].userId").value("user02"))
                // PagedModel의 페이지 정보 검증
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("userId를 통해 등록된 유저 정보 조회")
    void select_by_id() throws Exception {
        String userId = "IU";

        given(userService.selectById(any())).willReturn(IU);

        mockMvc.perform(get("/api/" + apiVersion + "/select/id/" + userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("아이유"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저를 조건으로 조회")
    void select_by_cond() throws Exception {
        List<UserDto> userList = List.of(IU);

        UserCond cond = UserCond.builder()
                .userId("IU").build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<UserDto> userPage = new PageImpl<>(userList, pageable, userList.size());

        given(userService.selectByCond(any())).willReturn(userPage);

        mockMvc.perform(post("/api/" + apiVersion + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                // PagedModel 구조에 맞게 검증 (content 필드 안에 리스트가 들어감)
                .andExpect(jsonPath("$.content[0].userId").value("IU"))
                // PagedModel의 페이지 정보 검증
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 정보 수정")
    void updateWithInfo() throws Exception {

        String userId = "update";
        String name = "업데이트";
        String email = "update@test.com";
        String phoneNumber = "010-3333-4444";

        UpdateUserInfo info = UpdateUserInfo.builder()
                .phoneNumber(phoneNumber)
                .email(email)
                .isBlacklisted(true)
                .build();

        UserDto userDto = UserDto.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .name(name)
                .phoneNumber(phoneNumber)
                .email(email)
                .birthDate(LocalDate.of(2000, 1, 1))
                .isBlacklisted(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(userService.update(any(), any())).willReturn(userDto);

        mockMvc.perform(put("/api/" + apiVersion + "/update/id/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value(phoneNumber))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(true));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("유저 삭제")
    void deleteById() throws Exception {

        String userId = "IU";

        given(userService.delete(any())).willReturn(IU);

        mockMvc.perform(delete("/api/" + apiVersion + "/delete/id/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value("IU"))
                .andExpect(jsonPath("$.name").value("아이유"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.email").value("IU@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0516-0918"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.isBlacklisted").value(false));

    }
}