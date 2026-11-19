package dev.bum.queue_service.service.reactive;

import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.queue_service.exception.EventPolicyException;
import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventQueuePolicyStore {

    // 대기열 상태와 토큰을 저장하고 조회하는 Redis 클라이언트.
    private final ReactiveStringRedisTemplate redis;
    // 대기열 작업에 사용할 Redis 키 생성 도구.
    private final QueueRedisKeys keys;

    /**
     * 공연 처리에서 전달받은 변경 사항을 반영한다.
     */
    public Mono<Long> apply(EventQueuePolicyEvent event) {

        return Mono.defer(() -> {
            if (event.eventId() == null || event.eventId() <= 0 || event.version() <= 0
                    || event.status() == null || event.admissionSize() <= 0) {
                return Mono.error(new IllegalArgumentException("공연 정책 이벤트가 올바르지 않습니다."));
            }

            return redis.execute(QueueRedisScripts.UPDATE_POLICY, List.of(keys.policyKey(event.eventId())),
                    List.of(Long.toString(event.version()), event.status(), Long.toString(event.saleStartAt()),
                            Long.toString(event.saleEndAt()), Long.toString(event.eventDateTime()),
                            Integer.toString(event.admissionSize()))).next()
                    .switchIfEmpty(Mono.error(new org.springframework.dao.DataAccessResourceFailureException(
                            "공연 정책 저장 결과를 확인할 수 없습니다.")));
        });
    }

    /**
     * 공연 처리에서 요청 값과 현재 상태가 유효한지 검증한다.
     */
    public Mono<Void> validate(Long eventId, boolean existing) {

        return redis.execute(QueueRedisScripts.VALIDATE_POLICY, List.of(keys.policyKey(eventId)),
                        List.of(existing ? "1" : "0")).next()
                .defaultIfEmpty("POLICY_UNAVAILABLE")
                .flatMap(result -> "OK".equals(result) ? Mono.empty() : Mono.error(new EventPolicyException(result)));
    }
}
