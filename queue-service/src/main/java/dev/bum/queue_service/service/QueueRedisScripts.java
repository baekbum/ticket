package dev.bum.queue_service.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 동기·Reactive 구현이 동일한 입장 승인 규칙을 사용한다. */
public final class QueueRedisScripts {
    public static final RedisScript<Long> ADMIT_IF_SLOT_AVAILABLE =
            RedisScript.of(new ClassPathResource("redis/queue-admit.lua"), Long.class);

    private QueueRedisScripts() {
    }
}
