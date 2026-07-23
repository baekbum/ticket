package dev.bum.ticket_service.service.queue;

import dev.bum.ticket_service.config.QueueAccessProperties;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.feign.queue.QueueServiceClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class QueueAccessService {

    private final QueueAccessProperties properties;
    private final QueueServiceClient queueServiceClient;

    public QueueAccessService(QueueAccessProperties properties, QueueServiceClient queueServiceClient) {
        this.properties = properties;
        this.queueServiceClient = queueServiceClient;
    }

    public void validate(Long eventId, String userId, String queueToken) {
        if (!properties.enabled()) {
            return;
        }
        if (eventId == null) {
            throw new QueueAccessDeniedException("대기열 검증을 위한 이벤트 ID가 필요합니다.");
        }
        if (!StringUtils.hasText(userId)) {
            throw new QueueAccessDeniedException("사용자 인증 정보가 필요합니다.");
        }
        if (!StringUtils.hasText(queueToken)) {
            throw new QueueAccessDeniedException("대기열 통과 토큰이 필요합니다.");
        }

        QueueValidateResponse response;
        try {
            response = queueServiceClient.validate(new QueueValidateRequest(eventId, userId, queueToken));
        } catch (FeignException e) {
            log.warn("[QUEUE-VALIDATE] queue-service 호출 실패. eventId={}, userId={}", eventId, userId, e);
            throw new QueueAccessDeniedException("대기열 서버 검증에 실패했습니다.");
        }

        if (response == null || !response.allowed()) {
            throw new QueueAccessDeniedException("대기열을 통과한 사용자만 티켓팅을 진행할 수 있습니다.");
        }
    }
}
