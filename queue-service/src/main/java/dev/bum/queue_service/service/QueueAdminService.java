package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAdminService {

    private final WaitingQueueService waitingQueueService;
    private final ActiveQueueService activeQueueService;
    private final UserQueueSessionService userQueueSessionService;

    @Observed(name = "queue.admin.statuses", contextualName = "queue admin bulk statuses")
    public List<QueueStatusResponse> statuses(Long eventId, List<String> userIds, Map<String, String> tokenByUserId) {
        return executeWithRedisLogging("adminStatuses", eventId, String.join(",", userIds), null, () -> {
            userIds.forEach(this::validateUserId);
            userIds.forEach(userQueueSessionService::prune);

            Map<String, String> activeTokenByUserId = activeQueueService.activeTokenByUserId(eventId, userIds);
            List<QueueStatusResponse> responses = new ArrayList<>();

            for (String userId : userIds) {
                String activeToken = activeTokenByUserId.get(userId);
                if (activeToken != null) {
                    responses.add(activeQueueService.readyStatusResponse(eventId, activeToken));
                    continue;
                }

                String waitingToken = tokenByUserId == null ? null : tokenByUserId.get(userId);
                responses.add(waitingStatus(eventId, userId, waitingToken));
            }

            return responses;
        });
    }

    private QueueStatusResponse waitingStatus(Long eventId, String userId, String waitingToken) {
        if (!waitingQueueService.isWaitingTokenValid(eventId, userId, waitingToken)) {
            throw new QueueTokenInvalidException("올바르지 않은 시도입니다. 다시 시도해주세요.");
        }

        waitingQueueService.refreshWaitingToken(eventId, userId, waitingToken);
        String activeToken = activeQueueService.admit(eventId, userId, waitingToken);
        if (activeToken != null) {
            return activeQueueService.readyStatusResponse(eventId, activeToken);
        }

        return waitingQueueService.waitingStatusResponse(eventId, userId, waitingToken);
    }

    private <T> T executeWithRedisLogging(
            String operation,
            Long eventId,
            String userId,
            String token,
            Supplier<T> supplier
    ) {
        try {
            return supplier.get();
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Queue Redis 처리 실패. operation={}, keyPrefix=queue, eventId={}, userId={}, token={}",
                    operation, eventId, userId, token, e);
            throw e;
        }
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
        }
    }
}
