package dev.bum.user_service.jpa;

import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.enums.AddressStatus;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.config.QuerydslConfig;
import dev.bum.user_service.exception.UserAddressNotExistException;
import dev.bum.user_service.jpa.address.UserAddress;
import dev.bum.user_service.jpa.address.UserAddressJpaRepository;
import dev.bum.user_service.jpa.address.UserAddressRepositoryImpl;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import dev.bum.user_service.jpa.user.UserRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({
        UserAddressRepositoryImpl.class,
        UserRepositoryImpl.class,
        QuerydslConfig.class,
        UserAddressRepositoryImplTest.TestPasswordEncoderConfig.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserAddressRepositoryImplTest {

    @Autowired
    private UserAddressRepositoryImpl userAddressRepository;

    @Autowired
    private UserAddressJpaRepository userAddressJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userJpaRepository.save(user("user01"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("주소 등록")
    void address_insert() {
        UserAddress saved = userAddressRepository.insert("user01", insertRequest("home", false));
        entityManager.flush();
        entityManager.clear();

        UserAddress response = userAddressRepository.selectById(saved.getAddressId());

        assertThat(response.getUser().getUserId()).isEqualTo("user01");
        assertThat(response.getAlias()).isEqualTo("home");
        assertThat(response.getDefaultAddress()).isTrue();
        assertThat(response.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    }

    @Test
    @DisplayName("기본 배송지로 등록하면 기존 기본 배송지를 해제")
    void address_insert_default_unsets_old_default_address() {
        UserAddress first = userAddressRepository.insert("user01", insertRequest("home", true));
        entityManager.flush();
        entityManager.clear();

        UserAddress second = userAddressRepository.insert("user01", insertRequest("office", true));
        entityManager.flush();
        entityManager.clear();

        UserAddress firstResponse = userAddressRepository.selectById(first.getAddressId());
        UserAddress secondResponse = userAddressRepository.selectById(second.getAddressId());

        assertThat(firstResponse.getDefaultAddress()).isFalse();
        assertThat(secondResponse.getDefaultAddress()).isTrue();
    }

    @Test
    @DisplayName("ID로 주소 조회")
    void address_select_by_id() {
        UserAddress saved = userAddressRepository.insert("user01", insertRequest("home", true));
        entityManager.flush();
        entityManager.clear();

        UserAddress response = userAddressRepository.selectById(saved.getAddressId());

        assertThat(response.getAddressId()).isEqualTo(saved.getAddressId());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 주소 조회 시 예외 발생")
    void address_select_by_id_fail() {
        assertThatThrownBy(() -> userAddressRepository.selectById(999L))
                .isInstanceOf(UserAddressNotExistException.class);
    }

    @Test
    @DisplayName("조건으로 주소 조회")
    void address_select_by_cond() {
        userAddressRepository.insert("user01", insertRequest("home", true));
        userAddressRepository.insert("user01", insertRequest("office", false));
        entityManager.flush();
        entityManager.clear();
        UserAddressCondRequest cond = UserAddressCondRequest.builder()
                .userId("user01")
                .status(AddressStatus.ACTIVE)
                .build();

        Page<UserAddress> response = userAddressRepository.selectByCond(
                cond,
                PageRequest.of(0, 10, Sort.by("addressId").descending())
        );

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent()).extracting(UserAddress::getAlias)
                .containsExactly("office", "home");
    }

    @Test
    @DisplayName("주소 수정")
    void address_update() {
        UserAddress saved = userAddressRepository.insert("user01", insertRequest("home", true));
        entityManager.flush();
        entityManager.clear();
        UpdateUserAddressRequest info = UpdateUserAddressRequest.builder()
                .alias("office")
                .detailAddress("202")
                .build();

        UserAddress updated = userAddressRepository.update(saved.getAddressId(), info);
        entityManager.flush();
        entityManager.clear();

        UserAddress response = userAddressRepository.selectById(updated.getAddressId());
        assertThat(response.getAlias()).isEqualTo("office");
        assertThat(response.getDetailAddress()).isEqualTo("202");
    }

    @Test
    @DisplayName("주소 삭제")
    void address_delete() {
        UserAddress saved = userAddressRepository.insert("user01", insertRequest("home", true));
        entityManager.flush();
        entityManager.clear();

        UserAddress deleted = userAddressRepository.delete(saved.getAddressId());
        entityManager.flush();
        entityManager.clear();

        UserAddress response = userAddressRepository.selectById(deleted.getAddressId());
        assertThat(response.getStatus()).isEqualTo(AddressStatus.DELETED);
        assertThat(response.getDefaultAddress()).isFalse();
    }

    @Test
    @DisplayName("사용자의 기본 배송지 해제")
    void unset_default_addresses() {
        UserAddress saved = userAddressRepository.insert("user01", insertRequest("home", true));
        entityManager.flush();
        entityManager.clear();

        userAddressRepository.unsetDefaultAddresses("user01");
        entityManager.flush();
        entityManager.clear();

        UserAddress response = userAddressRepository.selectById(saved.getAddressId());
        assertThat(response.getDefaultAddress()).isFalse();
    }

    private InsertUserAddressRequest insertRequest(String alias, Boolean defaultAddress) {
        return InsertUserAddressRequest.builder()
                .alias(alias)
                .recipientName("User")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address("Seoul")
                .detailAddress("101")
                .defaultAddress(defaultAddress)
                .build();
    }

    private User user(String userId) {
        return User.builder()
                .userId(userId)
                .password("password")
                .role(UserRole.ROLE_USER)
                .name("User")
                .phoneNumber("01012345678")
                .email(userId + "@test.com")
                .isBlacklisted(false)
                .build();
    }

    @TestConfiguration
    static class TestPasswordEncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return NoOpPasswordEncoder.getInstance();
        }
    }
}
