package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

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
        properties.setActiveTokenTtl(Duration.ofMinutes(10));
        queueService = new QueueService(redisTemplate, properties);

        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(zSetOperations.rangeByScore(eq("queue:event:1:active"), eq(0.0), any(Double.class))).thenReturn(Set.of());
        lenient().when(zSetOperations.rangeByScore(eq("queue:event:1:waiting-expiry"), eq(0.0), any(Double.class))).thenReturn(Set.of());
    }

    @Test
    @DisplayName("대기열 통과 시 Redis 스크립트로 active 슬롯 확인과 토큰 발급을 원자 처리한다")
    void status_admits_with_redis_script() {
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
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", null);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.waitingCount()).isEqualTo(1L);
        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("waiting token 없이 재접속하면 기존 대기 항목을 제거하고 새로 등록한다")
    void status_without_waiting_token_restarts_waiting_entry() {
        given(valueOperations.get("queue:waiting-user:1:user01")).willReturn("old-waiting-token");
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", null);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.token()).isNotEqualTo("old-waiting-token");
        then(zSetOperations).should().remove("queue:event:1:waiting", "old-waiting-token");
        then(zSetOperations).should().remove("queue:event:1:waiting-expiry", "old-waiting-token");
        then(redisTemplate).should().delete(List.of("queue:waiting-token:old-waiting-token", "queue:waiting-user:1:user01"));
    }

    @Test
    @DisplayName("대기열 이탈 요청은 유효한 waiting token을 정리한다")
    void leaveWaiting_removes_valid_waiting_token() {
        given(valueOperations.get("queue:waiting-token:waiting-token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-1")).willReturn((double) System.currentTimeMillis());

        boolean left = queueService.leaveWaiting(1L, "user01", "waiting-token-1");

        assertThat(left).isTrue();
        then(zSetOperations).should().remove("queue:event:1:waiting", "waiting-token-1");
        then(zSetOperations).should().remove("queue:event:1:waiting-expiry", "waiting-token-1");
        then(redisTemplate).should().delete(List.of("queue:waiting-token:waiting-token-1", "queue:waiting-user:1:user01"));
    }

    @Test
    @DisplayName("유효한 active token을 제시하면 READY 상태를 복구한다")
    void status_restores_ready_only_with_valid_token() {
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(0L);

        QueueStatusResponse response = queueService.status(1L, "user01", "token-1");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.token()).isEqualTo("token-1");
        assertThat(response.expiresInSeconds()).isPositive();
    }

    @Test
    @DisplayName("유효한 active 토큰 완료 시 active ZSet, active-token key, active-user key를 함께 정리한다")
    void complete_removes_active_token_and_user_mapping() {
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);

        boolean completed = queueService.complete(1L, "user01", "token-1");

        assertThat(completed).isTrue();
        then(zSetOperations).should().remove("queue:event:1:active", "token-1");
        then(redisTemplate).should().delete(List.of("queue:active-token:token-1", "queue:active-user:1:user01"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰 완료 요청은 Redis 상태를 변경하지 않는다")
    void complete_returns_false_without_cleanup_when_token_is_invalid() {
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:other-user");

        boolean completed = queueService.complete(1L, "user01", "token-1");

        assertThat(completed).isFalse();
        then(zSetOperations).should(never()).remove(anyString(), anyString());
        then(redisTemplate).should(never()).delete(anyList());
    }

    @Test
    @DisplayName("Redis 장애는 대기열 경계에서 로깅 후 전파한다")
    void status_propagates_redis_error_after_context_logging() {
        given(zSetOperations.add(eq("queue:event:1:waiting"), anyString(), any(Double.class)))
                .willThrow(new DataAccessException("redis error") {});

        assertThatThrownBy(() -> queueService.status(1L, "user01", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessage("redis error");
    }

    @Test
    @DisplayName("bulk 상태 조회는 기존 active-user 매핑이 유효하면 READY를 유지하고 재입장시키지 않는다")
    void statuses_reuses_valid_active_user_mapping() {
        given(valueOperations.get("queue:active-user:1:user01")).willReturn("token-1");
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(valueOperations.get("queue:active-user:1:user02")).willReturn(null);
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        List<QueueStatusResponse> responses = queueService.statuses(1L, List.of("user01", "user02"));

        assertThat(responses).extracting(QueueStatusResponse::status)
                .containsExactly("READY", "WAITING");
        assertThat(responses.get(0).token()).isEqualTo("token-1");
        assertThat(responses.get(1).rank()).isEqualTo(1L);
    }
}
