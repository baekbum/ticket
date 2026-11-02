package dev.bum.queue_service.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reactive 대기열의 원자적 Redis 상태 전이에 사용하는 스크립트. */
public final class QueueRedisScripts {
    public static final RedisScript<String> STATUS_WITH_IDEMPOTENCY =
            RedisScript.of(read("redis/queue-session-expiry.lua") + read("redis/queue-policy-check.lua")
                    + read("redis/queue-status.lua"), String.class);

    public static final RedisScript<String> VALIDATE_POLICY =
            RedisScript.of(read("redis/queue-policy-check.lua") + read("redis/queue-policy-validate.lua"), String.class);
    public static final RedisScript<Long> UPDATE_POLICY = load("redis/queue-policy-update.lua");

    public static final RedisScript<Long> REGISTER_WAITING =
            RedisScript.of(read("redis/queue-session-expiry.lua") + read("redis/queue-policy-check.lua")
                    + read("redis/queue-register.lua"), Long.class);
    public static final RedisScript<Long> REFRESH_WAITING =
            withSessionExpiry("redis/queue-refresh.lua");
    public static final RedisScript<Long> CLEAR_EVENT =
            load("redis/queue-clear-event.lua");
    public static final RedisScript<Long> REMOVE_TOKEN =
            withSessionExpiry("redis/queue-remove.lua");
    public static final RedisScript<Long> REMOVE_USER_SESSION = withSessionExpiry("redis/queue-session-remove.lua");
    public static final RedisScript<Long> PRUNE_USER_SESSIONS = withSessionExpiry("redis/queue-session-prune.lua");

    private static RedisScript<Long> withSessionExpiry(String path) {
        return RedisScript.of(read("redis/queue-session-expiry.lua") + read(path), Long.class);
    }

    private static RedisScript<Long> load(String path) {
        return RedisScript.of(read(path), Long.class);
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("대기열 Redis 스크립트를 읽을 수 없습니다: " + path, error);
        }
    }

    private QueueRedisScripts() {
    }
}
