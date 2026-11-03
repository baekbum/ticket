package dev.bum.queue_service.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reactive 대기열의 원자적 Redis 상태 전이에 사용하는 스크립트. */
public final class QueueRedisScripts {

    // 중복 요청을 확인하며 대기열 상태를 조회하는 Redis 스크립트.
    public static final RedisScript<String> STATUS_WITH_IDEMPOTENCY =
            RedisScript.of(read("redis/queue-session-expiry.lua") + read("redis/queue-policy-check.lua")
                    + read("redis/queue-status.lua"), String.class);
    // 공연 대기열 정책을 검증하는 Redis 스크립트.
    public static final RedisScript<String> VALIDATE_POLICY =
            RedisScript.of(read("redis/queue-policy-check.lua") + read("redis/queue-policy-validate.lua"), String.class);
    // 공연 대기열 정책을 갱신하는 Redis 스크립트.
    public static final RedisScript<Long> UPDATE_POLICY = load("redis/queue-policy-update.lua");
    // 대기열 사용자와 토큰을 등록하는 Redis 스크립트.
    public static final RedisScript<Long> REGISTER_WAITING =
            RedisScript.of(read("redis/queue-session-expiry.lua") + read("redis/queue-policy-check.lua")
                    + read("redis/queue-register.lua"), Long.class);
    // 대기 토큰과 세션 만료 시각을 갱신하는 Redis 스크립트.
    public static final RedisScript<Long> REFRESH_WAITING =
            withSessionExpiry("redis/queue-refresh.lua");
    // 공연의 대기열과 세션을 정리하는 Redis 스크립트.
    public static final RedisScript<Long> CLEAR_EVENT =
            load("redis/queue-clear-event.lua");
    // 대기열 토큰과 연결된 정보를 제거하는 Redis 스크립트.
    public static final RedisScript<Long> REMOVE_TOKEN =
            withSessionExpiry("redis/queue-remove.lua");
    // 사용자 대기열 세션을 제거하는 Redis 스크립트.
    public static final RedisScript<Long> REMOVE_USER_SESSION = withSessionExpiry("redis/queue-session-remove.lua");
    // 만료된 사용자 대기열 세션을 정리하는 Redis 스크립트.
    public static final RedisScript<Long> PRUNE_USER_SESSIONS = withSessionExpiry("redis/queue-session-prune.lua");

    /**
     * 외부에서 인스턴스를 생성하지 못하도록 제한한다.
     */
    private QueueRedisScripts() {
    }

    /**
     * 사용자 세션의 만료 시각 정보를 추가한다.
     */
    private static RedisScript<Long> withSessionExpiry(String path) {

        return RedisScript.of(read("redis/queue-session-expiry.lua") + read(path), Long.class);
    }

    /**
     * 대기열 정보를 조회한다.
     */
    private static RedisScript<Long> load(String path) {

        return RedisScript.of(read(path), Long.class);
    }

    /**
     * 대기열 정보를 조회한다.
     */
    private static String read(String path) {

        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("대기열 Redis 스크립트를 읽을 수 없습니다: " + path, error);
        }
    }
}
