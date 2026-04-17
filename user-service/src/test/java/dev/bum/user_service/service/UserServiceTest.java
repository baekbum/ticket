package dev.bum.user_service.service;

import dev.bum.user_service.dto.UserDto;
import dev.bum.user_service.enums.UserRole;
import dev.bum.user_service.jpa.User;
import dev.bum.user_service.jpa.UserRepository;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("유저 등록")
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

        User user = User.builder()
                .userId("IU")
                .password("IU05160918")
                .role(UserRole.ROLE_USER)
                .name("아이유")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .address("서울시 용산구 한남동")
                .isBlacklisted(false)
                .build();

        given(userRepository.insert(any())).willReturn(user);

        UserDto response = userService.insert(userInfo);

        assertThat(response.getUserId()).isEqualTo("IU");
        assertThat(response.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(response.getName()).isEqualTo("아이유");
        assertThat(response.getPhoneNumber()).isEqualTo("010-0516-0918");
        assertThat(response.getEmail()).isEqualTo("IU@test.com");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(1993, 5, 16));
        assertThat(response.getAddress()).isEqualTo("서울시 용산구 한남동");
        assertThat(response.getIsBlacklisted()).isFalse();

        verify(userRepository).insert(userInfo);
    }

    @Test
    @DisplayName("유저 전체 검색")
    void select_all() throws Exception {

        User admin = User.builder()
                .id(1L)
                .userId("admin")
                .role(UserRole.ROLE_ADMIN)
                .name("관리자")
                .isBlacklisted(false)
                .build();

        User user01 = User.builder()
                .id(2L)
                .userId("user01")
                .role(UserRole.ROLE_USER)
                .name("유저01")
                .isBlacklisted(false)
                .build();

        User user02 = User.builder()
                .id(3L)
                .userId("user02")
                .role(UserRole.ROLE_USER)
                .name("유저02")
                .isBlacklisted(false)
                .build();

        User blocked01 = User.builder()
                .id(4L)
                .userId("blocked01")
                .role(UserRole.ROLE_USER)
                .name("블락01")
                .isBlacklisted(true)
                .build();

        UserCond cond = UserCond.builder().build();

        List<User> userList = List.of(user01, user02, blocked01, admin);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<User> result = new PageImpl<>(userList, pageable, userList.size());

        given(userRepository.selectAll(any())).willReturn(result);

        Page<UserDto> response = userService.selectAll(cond);

        assertThat(response.getTotalElements()).isEqualTo(4);

        verify(userRepository).selectAll(argThat(pageRequest ->
                pageRequest.getPageNumber() == 0 && pageRequest.getPageSize() == 10
        ));
    }

    @Test
    @DisplayName("ID로 유저 검색")
    void select_by_userId() throws Exception {
        String userId = "IU";

        User result = User.builder()
                .userId("IU")
                .password("IU05160918")
                .role(UserRole.ROLE_USER)
                .name("아이유")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .address("서울시 용산구 한남동")
                .isBlacklisted(false)
                .build();

        given(userRepository.selectById(any())).willReturn(result);

        UserDto response = userService.selectById(userId);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getRole()).isEqualTo(response.getRole());
        assertThat(result.getName()).isEqualTo(response.getName());
        assertThat(result.getPhoneNumber()).isEqualTo(response.getPhoneNumber());
        assertThat(result.getEmail()).isEqualTo(response.getEmail());
        assertThat(result.getBirthDate()).isEqualTo(response.getBirthDate());
        assertThat(result.getAddress()).isEqualTo(response.getAddress());
        assertThat(result.getIsBlacklisted()).isEqualTo(response.getIsBlacklisted());

        verify(userRepository).selectById(userId);
    }

    @Test
    @DisplayName("Cond로 유저 검색")
    void select_by_cond() throws Exception {
        User admin = User.builder()
                .id(1L)
                .userId("admin")
                .role(UserRole.ROLE_ADMIN)
                .name("관리자")
                .isBlacklisted(false)
                .build();

        User user01 = User.builder()
                .id(2L)
                .userId("user01")
                .role(UserRole.ROLE_USER)
                .name("유저01")
                .isBlacklisted(false)
                .build();

        User user02 = User.builder()
                .id(3L)
                .userId("user02")
                .role(UserRole.ROLE_USER)
                .name("유저02")
                .isBlacklisted(false)
                .build();

        User blocked01 = User.builder()
                .id(4L)
                .userId("blocked01")
                .role(UserRole.ROLE_USER)
                .name("블락01")
                .isBlacklisted(true)
                .build();

        UserCond cond = UserCond.builder()
                .userIdList(List.of("user01", "admin"))
                .build();

        List<User> userList = List.of(user01, admin);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<User> result = new PageImpl<>(userList, pageable, userList.size());

        given(userRepository.selectByCond(any(), any())).willReturn(result);

        Page<UserDto> response = userService.selectByCond(cond);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent().get(0).getUserId()).isEqualTo("user01");
        assertThat(response.getContent().get(1).getUserId()).isEqualTo("admin");

        verify(userRepository).selectByCond(cond, pageable);
    }

    @Test
    @DisplayName("유저 정보 수정")
    void user_update() throws Exception {
        String userId = "user";

        UpdateUserInfo info = UpdateUserInfo.builder()
                .isBlacklisted(true)
                .build();

        User result = User.builder()
                .id(1L)
                .userId("user")
                .isBlacklisted(true)
                .build();

        given(userRepository.update(any(), any())).willReturn(result);

        UserDto response = userService.update(userId, info);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getIsBlacklisted()).isEqualTo(response.getIsBlacklisted());

        verify(userRepository).update(userId, info);
    }

    @Test
    @DisplayName("유저 삭제")
    void user_delete() throws Exception {
        String userId = "user";

        User result = User.builder()
                .id(1L)
                .userId("user")
                .name("유저")
                .build();

        given(userRepository.delete(any())).willReturn(result);

        UserDto response = userService.delete(userId);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getName()).isEqualTo(response.getName());

        verify(userRepository).delete(userId);
    }

}