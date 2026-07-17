package dev.bum.user_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.dto.DeleteUserBulkRequest;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.enums.UserGrade;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.exception.PasswordIncorrectException;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserRepository;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserCondRequest;
import dev.bum.user_service.service.user.UserService;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("유저 등록")
    void user_insert() throws Exception {
        InsertUserRequest userInfo = InsertUserRequest.builder()
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

        // NPE 방지를 위한 가짜 Future (제네릭 에러 방지를 위해 명시적 타입 지정)
        CompletableFuture<SendResult<String, UserDtoForEvent>> future = CompletableFuture.completedFuture(null);

        given(userRepository.insert(any())).willReturn(user);

        // 인자를 any()로 설정하면 토픽명이 null이어도 에러 없이 future를 반환합니다.
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        UserResponse response = userService.insert(userInfo);

        assertThat(response.getUserId()).isEqualTo("IU");
        assertThat(response.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(response.getGrade()).isEqualTo(UserGrade.GENERAL);
        assertThat(response.getName()).isEqualTo("아이유");
        assertThat(response.getPhoneNumber()).isEqualTo("010-0516-0918");
        assertThat(response.getEmail()).isEqualTo("IU@test.com");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(1993, 5, 16));
        assertThat(response.getAddress()).isEqualTo("서울시 용산구 한남동");
        assertThat(response.getIsBlacklisted()).isFalse();

        then(userRepository).should().insert(userInfo);
        then(kafkaTemplate).should(times(1)).send(any(), eq("IU"), any(UserDtoForEvent.class));
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

        UserCondRequest cond = UserCondRequest.builder().build();

        List<User> userList = List.of(user01, user02, blocked01, admin);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<User> result = new PageImpl<>(userList, pageable, userList.size());

        given(userRepository.selectByCond(any(), any())).willReturn(result);

        CustomPageResponse<UserResponse> response = userService.selectByCond(cond);

        assertThat(response.getPage().getTotalElements()).isEqualTo(4);

        then(userRepository).should().selectByCond(eq(cond), argThat(pageRequest ->
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

        UserResponse response = userService.selectById(userId);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getRole()).isEqualTo(response.getRole());
        assertThat(response.getGrade()).isEqualTo(UserGrade.GENERAL);
        assertThat(result.getName()).isEqualTo(response.getName());
        assertThat(result.getPhoneNumber()).isEqualTo(response.getPhoneNumber());
        assertThat(result.getEmail()).isEqualTo(response.getEmail());
        assertThat(result.getBirthDate()).isEqualTo(response.getBirthDate());
        assertThat(result.getAddress()).isEqualTo(response.getAddress());
        assertThat(result.getIsBlacklisted()).isEqualTo(response.getIsBlacklisted());

        then(userRepository).should().selectById(userId);
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

        UserCondRequest cond = UserCondRequest.builder()
                .userId("user01")
                .build();

        List<User> userList = List.of(user01);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        Page<User> result = new PageImpl<>(userList, pageable, userList.size());

        given(userRepository.selectByCond(any(), any())).willReturn(result);

        CustomPageResponse<UserResponse> response = userService.selectByCond(cond);

        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getUserId()).isEqualTo("user01");

        then(userRepository).should().selectByCond(cond, pageable);
    }

    @Test
    @DisplayName("유저 정보 수정")
    void user_update() throws Exception {
        String userId = "user";

        UpdateUserRequest info = UpdateUserRequest.builder()
                .isBlacklisted(true)
                .build();

        User result = User.builder()
                .id(1L)
                .userId("user")
                .role(UserRole.ROLE_USER)
                .isBlacklisted(true)
                .build();

        given(userRepository.selectById(any())).willReturn(result);
        given(userRepository.update(any(), any())).willReturn(result);

        UserResponse response = userService.update(userId, info);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getIsBlacklisted()).isEqualTo(response.getIsBlacklisted());

        then(userRepository).should().update(userId, info);
        then(kafkaTemplate).should(never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Grade 변경 시 Kafka 이벤트를 전송하지 않음")
    void user_update_grade_changed_does_not_send_event() {
        String userId = "user";
        UpdateUserRequest info = UpdateUserRequest.builder()
                .grade("VIP")
                .build();

        User originalUser = User.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .grade(UserGrade.GENERAL)
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .grade(UserGrade.VIP)
                .build();

        given(userRepository.selectById(userId)).willReturn(originalUser);
        given(userRepository.update(userId, info)).willReturn(updatedUser);

        UserResponse response = userService.update(userId, info);

        assertThat(response.getGrade()).isEqualTo(UserGrade.VIP);
        then(kafkaTemplate).should(never()).send(any(), any(), any());
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

        // NPE 방지를 위한 가짜 Future (제네릭 에러 방지를 위해 명시적 타입 지정)
        CompletableFuture<SendResult<String, UserDtoForEvent>> future = CompletableFuture.completedFuture(null);

        given(userRepository.delete(any())).willReturn(result);

        // 인자를 any()로 설정하면 토픽명이 null이어도 에러 없이 future를 반환합니다.
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        UserResponse response = userService.delete(userId);

        assertThat(result.getUserId()).isEqualTo(response.getUserId());
        assertThat(result.getName()).isEqualTo(response.getName());

        then(userRepository).should().delete(userId);
    }

    @Test
    @DisplayName("ID 중복 체크는 Repository에 위임")
    void is_duplicated() {
        String userId = "IU";

        userService.isDuplicated(userId);

        then(userRepository).should().isExist(userId);
    }

    @Test
    @DisplayName("Role 변경 시 Kafka 이벤트 전송")
    void user_update_role_changed_sends_event() {
        String userId = "user";
        UpdateUserRequest info = UpdateUserRequest.builder()
                .role("ROLE_ADMIN")
                .build();

        User originalUser = User.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .userId(userId)
                .role(UserRole.ROLE_USER)
                .build();

        CompletableFuture<SendResult<String, UserDtoForEvent>> future = CompletableFuture.completedFuture(null);

        given(userRepository.selectById(userId)).willReturn(originalUser);
        given(userRepository.update(userId, info)).willReturn(updatedUser);
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        userService.update(userId, info);

        then(kafkaTemplate).should().send(any(), eq(userId), argThat(event ->
                event.getEventType() == TopicEventType.UPDATE && event.getUserId().equals(userId)
        ));
    }

    @Test
    @DisplayName("비밀번호 검증 성공")
    void validate_info_success() {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder()
                .userId("IU")
                .password("plain-password")
                .build();

        User user = User.builder()
                .userId("IU")
                .password("encoded-password")
                .build();

        given(userRepository.selectById("IU")).willReturn(user);
        given(passwordEncoder.matches("plain-password", "encoded-password")).willReturn(true);

        userService.validateInfo(info);

        then(passwordEncoder).should().matches("plain-password", "encoded-password");
    }

    @Test
    @DisplayName("비밀번호 검증 실패")
    void validate_info_fail() {
        ValidatePasswordRequest info = ValidatePasswordRequest.builder()
                .userId("IU")
                .password("wrong-password")
                .build();

        User user = User.builder()
                .userId("IU")
                .password("encoded-password")
                .build();

        given(userRepository.selectById("IU")).willReturn(user);
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> userService.validateInfo(info))
                .isInstanceOf(PasswordIncorrectException.class);

        then(userRepository).should().selectById("IU");
        then(passwordEncoder).should().matches("wrong-password", "encoded-password");
    }

    @Test
    @DisplayName("비밀번호 초기화")
    void init_password() {
        String userId = "IU";

        userService.initPassword(userId);

        then(userRepository).should().update(eq(userId), argThat(info ->
                "123456789!".equals(info.getPassword())
        ));
    }

    @Test
    @DisplayName("벌크 삭제 시 모든 유저 삭제")
    void delete_bulk() {
        DeleteUserBulkRequest info = DeleteUserBulkRequest.builder()
                .userIds(List.of("user01", "user02"))
                .build();

        User user01 = User.builder().id(1L).userId("user01").build();
        User user02 = User.builder().id(2L).userId("user02").build();
        CompletableFuture<SendResult<String, UserDtoForEvent>> future = CompletableFuture.completedFuture(null);

        given(userRepository.delete("user01")).willReturn(user01);
        given(userRepository.delete("user02")).willReturn(user02);
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        userService.deleteBulk(info);

        then(userRepository).should().delete("user01");
        then(userRepository).should().delete("user02");
    }

    @Test
    @DisplayName("벌크 삭제 대상이 비어있으면 예외 발생")
    void delete_bulk_empty_ids() {
        DeleteUserBulkRequest info = DeleteUserBulkRequest.builder()
                .userIds(List.of())
                .build();

        assertThatThrownBy(() -> userService.deleteBulk(info))
                .isInstanceOf(IllegalArgumentException.class);

        then(userRepository).should(never()).delete(any());
    }

}
