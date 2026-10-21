package dev.bum.user_service.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEventJpaRepository;
import dev.bum.user_service.jpa.outbox.UserOutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserOutboxPublisher {
    private final UserOutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    @Value("${app.user-outbox.send-timeout-ms:10000}")
    private long sendTimeoutMs = 10000;

    @Value("${app.user-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long outboxId) {

        UserOutboxEvent event = repository.findByIdForUpdate(outboxId).orElse(null);

        if (event == null || event.getStatus() == UserOutboxStatus.PUBLISHED
                || event.getNextAttemptAt().isAfter(LocalDateTime.now())) return;

        // 아직 처리가 안된 앞선 이벤트가 존재한다면 현재 이벤트를 진행하지 않는다.
        if (repository.existsByUserIdAndStatusAndOutboxIdLessThan(
                event.getUserId(), UserOutboxStatus.PENDING, outboxId
        )) return;

        try {
            UserDtoForEvent payload = objectMapper.readValue(event.getPayload(), UserDtoForEvent.class);

            if (!StringUtils.hasText(payload.getIdempotencyKey())
                    || payload.getEventVersion() == null || payload.getEventVersion() < 1) {
                throw new IllegalArgumentException("회원 이벤트의 멱등키 또는 버전이 올바르지 않습니다.");
            }

            // .get()이 없다면 비동기로 발행하고 바로 다른 코드를 진행하지만, .get()을 사용하면 응답이 올 때까지 대기함.
            kafkaTemplate.send(event.getTopic(), event.getUserId(), payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // 직렬화 오류에도 비밀번호 등이 로그나 실패 사유에 노출되지 않도록 오류 유형만 남긴다.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            event.failed(cause.getClass().getSimpleName(), LocalDateTime.now().plusNanos(retryDelayMs * 1_000_000));
            log.warn("회원 이벤트 발행 실패: outboxId={}, userId={}, errorType={}", outboxId, event.getUserId(), cause.getClass().getSimpleName());
            return;
        }

        // Kafka 확인 응답을 받은 후 별도 트랜잭션에서 발행 완료를 확정한다.
        event.published(LocalDateTime.now());
    }
}
