package dev.bum.ticket_service.service.queue;

import dev.bum.common.service.queue.dto.QueueCompleteRequest;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.ticket_service.config.QueueAccessProperties;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.exception.queue.QueueUnavailableException;
import dev.bum.ticket_service.feign.queue.QueueServiceClient;
import feign.FeignException;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class QueueAccessService {

    // 처리에 사용할 설정.
    private final QueueAccessProperties properties;
    // 대기열 API를 호출하는 클라이언트.
    private final QueueServiceClient queueServiceClient;

    /**
     * 전달받은 값으로 대기열 객체를 초기화한다.
     */
    public QueueAccessService(QueueAccessProperties properties, QueueServiceClient queueServiceClient) {

        this.properties = properties;
        this.queueServiceClient = queueServiceClient;
    }

    /**
     * 대기열 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    @Observed(name = "ticket.queue.complete", contextualName = "ticket queue complete")
    public void complete(Long eventId, String userId, String activeToken) {

        if (!properties.enabled()) {
            return;
        }

        if (eventId == null || !StringUtils.hasText(userId) || !StringUtils.hasText(activeToken)) {
            log.warn("[QUEUE-COMPLETE] invalid complete request. eventId={}, userId={}", eventId, userId);
            return;
        }

        try {
            queueServiceClient.complete(new QueueCompleteRequest(eventId, userId, activeToken));
        } catch (FeignException e) {
            log.warn("[QUEUE-COMPLETE] queue-service 호출 실패. eventId={}, userId={}", eventId, userId, e);
        }
    }

    /**
     * 대기열 처리에서 요청 값과 현재 상태가 유효한지 검증한다.
     */
    @Observed(name = "ticket.queue.validate", contextualName = "ticket queue validate")
    public void validate(Long eventId, String userId, String activeToken) {

        if (!properties.enabled()) {
            return;
        }

        if (eventId == null) {
            throw new QueueAccessDeniedException("대기열 검증을 위한 이벤트 ID가 필요합니다.");
        }

        if (!StringUtils.hasText(userId)) {
            throw new QueueAccessDeniedException("사용자 인증 정보가 필요합니다.");
        }

        if (!StringUtils.hasText(activeToken)) {
            throw new QueueAccessDeniedException("active token이 필요합니다.");
        }

        QueueValidateResponse response;
        try {
            response = queueServiceClient.validate(new QueueValidateRequest(eventId, userId, activeToken));
        } catch (FeignException e) {
            if (e.status() == 409) {
                throw new QueueAccessDeniedException("판매 기간 또는 공연 상태에 따라 현재 예매할 수 없습니다.");
            }
            log.warn("[QUEUE-VALIDATE] queue-service 호출 실패. eventId={}, userId={}", eventId, userId, e);
            throw new QueueUnavailableException();
        }

        if (response == null) {
            throw new QueueUnavailableException();
        }

        if (!response.allowed() && "INVALID_QUEUE_TOKEN".equals(response.reason())) {
            throw new ActiveTokenExpiredException();
        }

        if (!response.allowed()) {
            throw new QueueAccessDeniedException("대기열을 통과한 사용자만 티켓팅을 진행할 수 있습니다.");
        }
    }
}
