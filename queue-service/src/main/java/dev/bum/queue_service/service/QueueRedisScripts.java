package dev.bum.queue_service.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 동기·Reactive 구현이 동일한 입장 승인 규칙을 사용한다. */
public final class QueueRedisScripts {
    public static final RedisScript<Long> ADMIT_IF_SLOT_AVAILABLE =
            load("redis/queue-admit.lua");

    public static final RedisScript<Long> REGISTER_WAITING =
            load("redis/queue-register.lua");
    public static final RedisScript<Long> REFRESH_WAITING =
            load("redis/queue-refresh.lua");
    public static final RedisScript<Long> REMOVE_TOKEN =
            load("redis/queue-remove.lua");

    private static RedisScript<Long> load(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return RedisScript.of(new String(input.readAllBytes(), StandardCharsets.UTF_8), Long.class);
        } catch (IOException error) {
            throw new UncheckedIOException("대기열 Redis 스크립트를 읽을 수 없습니다: " + path, error);
        }
    }

    private QueueRedisScripts() {
    }
}
