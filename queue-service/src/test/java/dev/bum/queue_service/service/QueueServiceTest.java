package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        QueueProperties properties = new QueueProperties();
        properties.setAdmissionSize(1);
        properties.setTokenTtl(Duration.ofMinutes(10));
        queueService = new QueueService(redisTemplate, properties);

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.rangeByScore(eq("queue:event:1:active"), eq(0.0), any(Double.class))).willReturn(Set.of());
    }

    @Test
    @DisplayName("대기열 통과 시 Redis 스크립트로 active 슬롯 확인과 토큰 발급을 원자 처리한다")
    void status_admits_with_redis_script() {
        given(zSetOperations.rank("queue:event:1:waiting", "user01")).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(0L);
        given(zSetOperations.score(eq("queue:event:1:active"), any(String.class))).willReturn((double) System.currentTimeMillis() + 600_000);
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", null);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.rank()).isZero();
        assertThat(response.token()).isNotBlank();
    }

    @Test
    @DisplayName("슬롯이 차 있으면 Redis 스크립트가 토큰 발급을 거절하고 WAITING을 유지한다")
    void status_waits_when_script_rejects_admission() {
        given(zSetOperations.rank("queue:event:1:waiting", "user01")).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", null);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.waitingCount()).isEqualTo(1L);
        assertThat(response.token()).isNull();
    }

    @Test
    @DisplayName("유효한 대기열 토큰을 제시하면 READY 상태를 복구한다")
    void status_restores_ready_only_with_valid_token() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("queue:token:token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(0L);

        QueueStatusResponse response = queueService.status(1L, "user01", "token-1");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.token()).isEqualTo("token-1");
        assertThat(response.expiresInSeconds()).isPositive();
    }
}
