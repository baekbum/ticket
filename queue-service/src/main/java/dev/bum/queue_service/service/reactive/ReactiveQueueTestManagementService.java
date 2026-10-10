package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.service.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
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

    /** 기존 테스트용 이벤트 삭제 API의 삭제 범위를 그대로 유지한다. */
    public Mono<Long> clearEventQueue(Long eventId) {
        return Flux.concat(Flux.just(keys.waitingKey(eventId), keys.activeKey(eventId)),
                        redis.opsForZSet().range(keys.activeKey(eventId), Range.closed(0L, -1L))
                                .concatMap(token -> redis.opsForValue().get(keys.activeTokenKey(token))
                                        .map(Optional::of).defaultIfEmpty(Optional.empty())
                                        .flatMapMany(value -> {
                                            String[] parts = value.orElse("").split(":", 2);
                                            List<String> tokenKeys = parts.length == 2
                                                    ? List.of(keys.activeTokenKey(token), "queue:active-user:" + parts[0] + ":" + parts[1])
                                                    : List.of(keys.activeTokenKey(token));
                                            return Flux.fromIterable(tokenKeys);
                                        })))
                .collectList().flatMap(deleteKeys -> redis.delete(Flux.fromIterable(deleteKeys)));
    }
}
