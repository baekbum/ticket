package dev.bum.queue_service.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reactive 대기열의 원자적 Redis 상태 전이에 사용하는 스크립트. */
public final class QueueRedisScripts {
    public static final RedisScript<String> STATUS_WITH_IDEMPOTENCY =
            RedisScript.of(read("redis/queue-status.lua"), String.class);

    public static final RedisScript<Long> REGISTER_WAITING =
            load("redis/queue-register.lua");
    public static final RedisScript<Long> REFRESH_WAITING =
            load("redis/queue-refresh.lua");
    public static final RedisScript<Long> CLEAR_EVENT =
            load("redis/queue-clear-event.lua");
    public static final RedisScript<Long> REMOVE_TOKEN =
            load("redis/queue-remove.lua");

    private static RedisScript<Long> load(String path) {
        return RedisScript.of(read(path), Long.class);
    }

    private static String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("대기열 Redis 스크립트를 읽을 수 없습니다: " + path, error);
        }
    }

    private QueueRedisScripts() {
    }
}
