package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.ticket_service.config.CaptchaPoolProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.captcha.pool", name = "enabled", matchIfMissing = true)
public class CaptchaImagePool {

    static final String KEY = "ticket:captcha:prepared:v1";

    // 각 인스턴스가 먼저 조회한 목록 길이는 바뀔 수 있으므로 저장 시 Redis에서 다시 확인한다.
    private static final DefaultRedisScript<Long> APPEND_UP_TO_CAPACITY = new DefaultRedisScript<>("""
            local remaining = tonumber(ARGV[1]) - redis.call('LLEN', KEYS[1])
            if remaining <= 0 then
                return 0
            end
            local count = math.min(remaining, #ARGV - 1)
            for i = 1, count do
                redis.call('RPUSH', KEYS[1], ARGV[i + 1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CaptchaImageGenerator generator;
    private final CaptchaPoolProperties properties;

    @Scheduled(fixedDelayString = "${app.captcha.pool.refill-delay-ms:1000}")
    public void refill() throws JsonProcessingException {
        Long size = redis.opsForList().size(KEY);
        if (size == null || size >= properties.capacity()) {
            return;
        }

        int count = Math.min(properties.batchSize(), properties.capacity() - size.intValue());
        Object[] arguments = new Object[count + 1];
        arguments[0] = Integer.toString(properties.capacity());
        for (int i = 0; i < count; i++) {
            arguments[i + 1] = objectMapper.writeValueAsString(generator.generate());
        }
        redis.execute(APPEND_UP_TO_CAPACITY, List.of(KEY), arguments);
    }
}
