package dev.bum.auth_service.jpa;

import dev.bum.auth_service.exception.UserAlreadyExistException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthRepositoryImplTest {

    @InjectMocks
    private AuthRepositoryImpl authRepository;

    @Mock
    private AuthJpaRepository jpaRepository;

    @Test
    @DisplayName("유저가 존재하지 않으면 중복 체크 통과")
    void is_exist_success() {
        given(jpaRepository.findByUserId("user01")).willReturn(Optional.empty());

        authRepository.isExist("user01");

        then(jpaRepository).should().findByUserId("user01");
    }

    @Test
    @DisplayName("유저가 이미 존재하면 중복 예외 발생")
    void is_exist_fail_with_duplicate_user() {
        Auth auth = auth("user01");

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.of(auth));

        assertThatThrownBy(() -> authRepository.isExist("user01"))
                .isInstanceOf(UserAlreadyExistException.class);

        then(jpaRepository).should().findByUserId("user01");
    }

    @Test
    @DisplayName("Auth 정보 저장")
    void insert() {
        UserDtoForEvent event = userEvent("user01");

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.empty());

        authRepository.insert(event);

        then(jpaRepository).should().findByUserId("user01");
        then(jpaRepository).should().save(argThat(auth ->
                auth.getId().equals(1L)
                        && auth.getUserId().equals("user01")
                        && auth.getPassword().equals("encoded-password")
                        && auth.getRole() == UserRole.ROLE_USER
        ));
    }

    @Test
    @DisplayName("중복 유저 저장 시 예외 발생")
    void insert_fail_with_duplicate_user() {
        UserDtoForEvent event = userEvent("user01");
        Auth auth = auth("user01");

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.of(auth));

        assertThatThrownBy(() -> authRepository.insert(event))
                .isInstanceOf(UserAlreadyExistException.class);

        then(jpaRepository).should().findByUserId("user01");
        then(jpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("ID로 Auth 조회")
    void find_by_id() {
        Auth auth = auth("user01");

        given(jpaRepository.findById(1L)).willReturn(Optional.of(auth));

        Auth response = authRepository.findById(1L);

        assertThat(response.getUserId()).isEqualTo("user01");
        then(jpaRepository).should().findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 예외 발생")
    void find_by_id_fail() {
        given(jpaRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authRepository.findById(99L))
                .isInstanceOf(UserNotExistException.class);

        then(jpaRepository).should().findById(99L);
    }

    @Test
    @DisplayName("userId로 Auth 조회")
    void find_by_user_id() {
        Auth auth = auth("user01");

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.of(auth));

        Auth response = authRepository.findByUserId("user01");

        assertThat(response.getUserId()).isEqualTo("user01");
        then(jpaRepository).should().findByUserId("user01");
    }

    @Test
    @DisplayName("존재하지 않는 userId 조회 시 예외 발생")
    void find_by_user_id_fail() {
        given(jpaRepository.findByUserId("none")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authRepository.findByUserId("none"))
                .isInstanceOf(UserNotExistException.class);

        then(jpaRepository).should().findByUserId("none");
    }

    @Test
    @DisplayName("Auth 정보 수정")
    void update() {
        Auth auth = auth("user01");
        UserDtoForEvent event = UserDtoForEvent.builder()
                .userId("user01")
                .password("new-password")
                .role("ROLE_ADMIN")
                .build();

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.of(auth));

        authRepository.update(event);

        assertThat(auth.getPassword()).isEqualTo("new-password");
        assertThat(auth.getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        then(jpaRepository).should().findByUserId("user01");
    }

    @Test
    @DisplayName("Auth 정보 삭제")
    void delete() {
        Auth auth = auth("user01");

        given(jpaRepository.findByUserId("user01")).willReturn(Optional.of(auth));

        authRepository.delete("user01");

        then(jpaRepository).should().findByUserId("user01");
        then(jpaRepository).should().delete(auth);
    }

    private Auth auth(String userId) {
        return Auth.builder()
                .id(1L)
                .userId(userId)
                .password("encoded-password")
                .role(UserRole.ROLE_USER)
                .build();
    }

    private UserDtoForEvent userEvent(String userId) {
        return UserDtoForEvent.builder()
                .id(1L)
                .userId(userId)
                .password("encoded-password")
                .role("ROLE_USER")
                .build();
    }
}
