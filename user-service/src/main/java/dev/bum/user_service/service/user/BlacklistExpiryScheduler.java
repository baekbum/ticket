package dev.bum.user_service.service.user;

import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistExpiryScheduler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final UserJpaRepository userJpaRepository;
    private final UserService userService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void releaseExpiredBlacklists() {
        LocalDate today = LocalDate.now(ZONE);
        for (User user : userJpaRepository.findByIsBlacklistedTrueAndBlacklistedUntilLessThan(today)) {
            try {
                userService.update(user.getUserId(), UpdateUserRequest.builder().isBlacklisted(false).build());
            } catch (RuntimeException ex) {
                log.error("{} 사용자의 차단 해제가 실패하였습니다.", user.getUserId(), ex);
            }
        }
    }
}
