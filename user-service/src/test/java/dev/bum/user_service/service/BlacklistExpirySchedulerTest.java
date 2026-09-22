package dev.bum.user_service.service;

import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import dev.bum.user_service.service.user.BlacklistExpiryScheduler;
import dev.bum.user_service.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlacklistExpirySchedulerTest {
    @InjectMocks BlacklistExpiryScheduler scheduler;
    @Mock UserJpaRepository userJpaRepository;
    @Mock UserService userService;

    @Test
    void releases_expired_users_through_user_service() {
        User expired = User.builder().userId("user01").isBlacklisted(true)
                .blacklistedUntil(LocalDate.now().minusDays(1)).build();
        given(userJpaRepository.findByIsBlacklistedTrueAndBlacklistedUntilLessThan(any()))
                .willReturn(List.of(expired));

        scheduler.releaseExpiredBlacklists();

        verify(userService).update(org.mockito.ArgumentMatchers.eq("user01"),
                argThat((UpdateUserRequest request) -> Boolean.FALSE.equals(request.getIsBlacklisted())));
        verify(userJpaRepository).findByIsBlacklistedTrueAndBlacklistedUntilLessThan(
                argThat(today -> today.isAfter(expired.getBlacklistedUntil())));
    }
}
