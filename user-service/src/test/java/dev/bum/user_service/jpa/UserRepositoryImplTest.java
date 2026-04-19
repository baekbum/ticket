package dev.bum.user_service.jpa;

import dev.bum.user_service.config.CommonConfig;
import dev.bum.user_service.config.QuerydslConfig;
import dev.bum.user_service.enums.UserRole;
import dev.bum.user_service.exception.UserDuplicateException;
import dev.bum.user_service.exception.UserNotExistException;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({UserRepositoryImpl.class, QuerydslConfig.class, CommonConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // H2 같은 내장 DB 사용 강제
class UserRepositoryImplTest {

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private UserJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        User admin = User.builder()
                .userId("admin")
                .password("admin1234!")
                .role(UserRole.ROLE_ADMIN)
                .name("관리자")
                .phoneNumber("010-1111-1111")
                .email("admin@test.com")
                .isBlacklisted(false)
                .build();

        User IU = User.builder()
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

        jpaRepository.save(admin);
        jpaRepository.save(IU);
    }

    @Test
    @DisplayName("유저 저장")
    void user_insert() throws Exception {
        InsertUserInfo info = InsertUserInfo.builder()
                .userId("addUser")
                .password("addUser1234!")
                .name("추가유저")
                .phoneNumber("010-1234-5678")
                .email("addUser@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("주소 없음")
                .build();

        User response = userRepository.insert(info);

        assertThat(response.getUserId()).isEqualTo(info.getUserId());
        assertThat(response.getPassword()).isEqualTo(info.getPassword());
        assertThat(response.getName()).isEqualTo(info.getName());
        assertThat(response.getPhoneNumber()).isEqualTo(info.getPhoneNumber());
        assertThat(response.getEmail()).isEqualTo(info.getEmail());
        assertThat(response.getBirthDate()).isEqualTo(info.getBirthDate());
    }

    @Test
    @DisplayName("유저 저장 시 이미 아이디가 존재하면 오류를 반환")
    void fail_already_exist() throws Exception {
        String userId = "IU";

        InsertUserInfo info = InsertUserInfo.builder()
                .userId(userId)
                .password("IU05160918")
                .name("아이유")
                .phoneNumber("010-0516-0918")
                .email("IU@test.com")
                .birthDate(LocalDate.of(1993, 5, 16))
                .address("서울시 용산구 한남동")
                .build();

        assertThatThrownBy(() -> userRepository.insert(info))
                .isInstanceOf(UserDuplicateException.class)
                .hasMessageContaining("해당 사용자 ID는 이미 존재합니다.");
    }

    @Test
    @DisplayName("유저 전체 조회")
    void select_all() throws Exception {
        InsertUserInfo user01 = InsertUserInfo.builder()
                .userId("user01")
                .password("user1234!")
                .name("유저01")
                .phoneNumber("010-2222-1111")
                .email("user01@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("주소 없음")
                .build();

        InsertUserInfo user02 = InsertUserInfo.builder()
                .userId("user02")
                .password("user1234!")
                .name("유저02")
                .phoneNumber("010-3333-1111")
                .email("user02@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("주소 없음")
                .build();

        userRepository.insert(user01);
        userRepository.insert(user02);

        UserCond cond = UserCond.builder().build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<User> response = userRepository.selectAll(pageable);

        assertThat(response.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("ID로 유저 검색")
    void select_by_id() throws Exception {
        String userId = "IU";

        User user = userRepository.selectById(userId);

        assertThat(user.getUserId()).isEqualTo("IU");
        assertThat(user.getPassword()).isEqualTo("IU05160918");
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getName()).isEqualTo("아이유");
        assertThat(user.getPhoneNumber()).isEqualTo("010-0516-0918");
        assertThat(user.getEmail()).isEqualTo("IU@test.com");
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1993, 5, 16));
        assertThat(user.getAddress()).isEqualTo("서울시 용산구 한남동");
        assertThat(user.getIsBlacklisted()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 ID로 검색시 null 반환")
    void return_null_with_not_exist_id() {
        String userId = "notExistId";

        assertThatThrownBy(() -> userRepository.selectById(userId))
                .isInstanceOf(UserNotExistException.class)
                .hasMessageContaining("해당 유저를 발견하지 못했습니다.");
    }

    @Test
    @DisplayName("조건을 통해 유저 검색")
    void select_by_cond() throws Exception {

        InsertUserInfo user01 = InsertUserInfo.builder()
                .userId("user01")
                .password("user1234!")
                .name("유저01")
                .phoneNumber("010-2222-1111")
                .email("user01@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("주소 없음")
                .build();

        InsertUserInfo user02 = InsertUserInfo.builder()
                .userId("user02")
                .password("user1234!")
                .name("유저02")
                .phoneNumber("010-3333-1111")
                .email("user02@test.com")
                .birthDate(LocalDate.of(2000, 1, 1))
                .address("주소 없음")
                .build();

        userRepository.insert(user01);
        userRepository.insert(user02);

        UserCond cond = UserCond.builder()
                .userIdList(List.of("user01", "user02"))
                .build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<User> response = userRepository.selectByCond(cond, pageable);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent().get(0).getUserId()).isEqualTo("user01");
        assertThat(response.getContent().get(1).getUserId()).isEqualTo("user02");
    }

    @Test
    @DisplayName("조건에 해당하는 데이터가 0건인 경우")
    void return_zero_data() throws Exception {
        UserCond cond = UserCond.builder().address("존재하지 않는 주소").build();

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());

        Page<User> response = userRepository.selectByCond(cond, pageable);

        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("유저 정보 수정")
    void user_info_update() throws Exception {
        String userId = "IU";

        UpdateUserInfo info = UpdateUserInfo.builder()
                .phoneNumber("010-8888-9999")
                .email("update@test.com")
                .build();

        User updatedUser = userRepository.update(userId, info);

        assertThat(updatedUser.getUserId()).isEqualTo("IU");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("010-8888-9999");
        assertThat(updatedUser.getEmail()).isEqualTo("update@test.com");
    }

    @Test
    @DisplayName("유저 정보 삭제")
    void user_info_delete() throws Exception {
        String userId = "IU";

        userRepository.delete(userId);

        assertThatThrownBy(() -> userRepository.selectById(userId))
                .isInstanceOf(UserNotExistException.class)
                .hasMessageContaining("해당 유저를 발견하지 못했습니다.");
    }
}