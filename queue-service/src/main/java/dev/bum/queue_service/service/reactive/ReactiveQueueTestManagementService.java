package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReactiveQueueTestManagementService {
    private final ReactiveStringRedisTemplate redis;
    private final QueueRedisKeys keys;
    private final ReactiveActiveQueueService active;

    /** 기존 이벤트 키와 활성 토큰에 더해 승격 매핑도 제거한다. */
    public Mono<Long> clearEventQueue(Long eventId) {
        return clearEventQueue(eventId, 0);
    }

    private Mono<Long> clearEventQueue(Long eventId, int attempt) {
        return Mono.defer(() -> redis.opsForZSet().range(keys.activeKey(eventId), Range.closed(0L, -1L))
                .collectList()
                .flatMap(tokens -> Flux.concat(
                        Flux.just(keys.waitingKey(eventId), keys.activeKey(eventId)),
                        Flux.fromIterable(tokens)
                                .concatMap(token -> redis.opsForValue().get(keys.activeTokenKey(token))
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .flatMapMany(value -> {
                                            String[] parts = value.orElse("").split(":", 2);
                                            List<String> tokenKeys = parts.length == 2
                                                    ? List.of(keys.activeTokenKey(token), "queue:active-user:" + parts[0] + ":" + parts[1])
                                                    : List.of(keys.activeTokenKey(token));
                                            return Flux.concat(Flux.fromIterable(tokenKeys), active.mappingKeysForActive(token));
                                        })))
                        .collectList()
                        .flatMap(deleteKeys -> redis.execute(QueueRedisScripts.CLEAR_EVENT, deleteKeys, tokens)
                                .next()))
                .flatMap(removed -> {
                    if (removed >= 0) return Mono.just(removed);
                    // 조회 뒤 활성 목록이 달라지면 새 토큰·매핑까지 포함해 다시 삭제한다.
                    if (attempt < 2) return clearEventQueue(eventId, attempt + 1);
                    return Mono.error(new DataAccessResourceFailureException("변경 중인 이벤트의 활성 토큰을 삭제할 수 없습니다."));
                }));
    }
}