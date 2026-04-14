package dev.bum.auth_service.jpa;

import dev.bum.auth_service.exception.UserNotExistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@Transactional
@Import(AuthRepositoryImpl.class)
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // H2 같은 내장 DB 사용 강제
class AuthRepositoryImplTest {

    @Autowired
    private AuthRepositoryImpl authRepository;

    @Autowired
    private AuthJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        Auth sample = Auth.builder()
                .id(1L)
                .userId("testUser")
                .password("encoded_pw")
                .role("ROLE_USER")
                .build();

        jpaRepository.save(sample);
    }

    @Test
    @DisplayName("유저 저장 및 아이디로 조회 테스트")
    void save_and_find_test() {
          // 2. When (실제 저장 및 조회)
        Auth testUser = authRepository.findByUserId("testUser");

        // 3. Then (검증)
        assertThat(testUser).isNotNull();
        assertThat(testUser.getUserId()).isEqualTo("testUser");
    }

    @Test
    @DisplayName("유저가 없으면 예외가 발생한다")
    void find_fail_test() {
        // When & Then: 예외 발생 검증
        assertThatThrownBy(() -> authRepository.findByUserId("none"))
                .isInstanceOf(UserNotExistException.class)
                .hasMessageContaining("해당 유저를 발견하지 못했습니다.");
    }

}