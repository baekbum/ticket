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

    // 블랙리스트 만료일 계산에 사용할 시간대.
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    // 사용자 데이터를 조회하고 저장하는 레포지토리.
    private final UserJpaRepository userJpaRepository;
    // 사용자 업무 처리를 담당하는 서비스.
    private final UserService userService;

    /**
     * 적용 기간이 끝난 사용자 블랙리스트를 해제한다.
     */
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
