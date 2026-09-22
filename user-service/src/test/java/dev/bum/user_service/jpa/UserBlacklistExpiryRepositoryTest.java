package dev.bum.user_service.jpa;

import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserBlacklistExpiryRepositoryTest {
    @Autowired UserJpaRepository repository;

    @Test
    void end_date_is_inclusive() {
        LocalDate today = LocalDate.of(2026, 9, 22);
        repository.save(user("expired", "expired@example.com", "01000000001", today.minusDays(1)));
        repository.save(user("throughToday", "today@example.com", "01000000002", today));

        assertThat(repository.findByIsBlacklistedTrueAndBlacklistedUntilLessThan(today))
                .extracting(User::getUserId)
                .containsExactly("expired");
    }

    private User user(String userId, String email, String phoneNumber, LocalDate endDate) {
        return User.builder()
                .userId(userId)
                .password("encoded")
                .role(UserRole.ROLE_USER)
                .name("user")
                .phoneNumber(phoneNumber)
                .email(email)
                .isBlacklisted(true)
                .blacklistedUntil(endDate)
                .build();
    }
}
