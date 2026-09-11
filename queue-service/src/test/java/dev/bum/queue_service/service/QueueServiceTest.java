package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
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
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.atLeastOnce;
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

    private QueueProperties properties;
    private QueueRedisKeys keys;
    private QueueService queueService;
    private QueueAdminService queueAdminService;

    @BeforeEach
    void setUp() {
        properties = new QueueProperties();
        properties.setAdmissionSize(1);
        properties.setActiveTokenTtl(Duration.ofMinutes(10));
        keys = new QueueRedisKeys();
        UserQueueSessionService userQueueSessionService = new UserQueueSessionService(redisTemplate, keys);
        ActiveQueueService activeQueueService = new ActiveQueueService(
                redisTemplate,
                properties,
                keys,
                userQueueSessionService
        );
        WaitingQueueService waitingQueueService = new WaitingQueueService(
                redisTemplate,
                properties,
                keys,
                activeQueueService,
                userQueueSessionService
        );
        queueService = new QueueService(
                redisTemplate,
                properties,
                waitingQueueService,
                activeQueueService,
                userQueueSessionService
        );
        queueAdminService = new QueueAdminService(
                waitingQueueService,
                activeQueueService,
                userQueueSessionService
        );

        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(zSetOperations.score(anyString(), anyString())).thenReturn(null);
        lenient().when(zSetOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());
        lenient().when(zSetOperations.zCard(anyString())).thenReturn(0L);
        lenient().when(zSetOperations.rangeByScore(eq("queue:event:1:active"), eq(0.0), any(Double.class))).thenReturn(Set.of());
        lenient().when(zSetOperations.rangeByScore(eq("queue:event:1:waiting-expiry"), eq(0.0), any(Double.class))).thenReturn(Set.of());
    }

    @Test
    @DisplayName("세션 수가 제한에 도달하면 강제 진입 확인이 필요하다고 응답한다")
    void enter_returns_confirm_required_when_session_limit_reached() {
        properties.setMaxSessionsPerUser(1);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        given(zSetOperations.zCard("queue:user-sessions:user01")).willReturn(1L);

        QueueEnterResponse response = queueService.enter(1L, "user01", false);

        assertThat(response.status()).isEqualTo("SESSION_LIMIT_CONFIRM_REQUIRED");
        then(zSetOperations).should(never()).add(eq("queue:event:1:waiting"), anyString(), any(Double.class));
    }

    @Test
    @DisplayName("강제 진입이면 가장 빨리 만료되는 waiting 세션을 제거하고 새 세션을 만든다")
    void enter_force_removes_first_expiring_waiting_session() {
        properties.setMaxSessionsPerUser(1);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:user-sessions:user01")).willReturn(1L);
        given(zSetOperations.range("queue:user-sessions:user01", 0, -1)).willReturn(Set.of("waiting:old-waiting-token"));
        given(zSetOperations.range("queue:user-sessions:user01", 0, 0)).willReturn(Set.of("waiting:old-waiting-token"));
        given(redisTemplate.getExpire("queue:waiting-token:old-waiting-token")).willReturn(30L);

        QueueEnterResponse response = queueService.enter(1L, "user01", true);

        assertThat(response.status()).isEqualTo("WAITING");
        then(zSetOperations).should().remove("queue:event:1:waiting", "old-waiting-token");
        then(zSetOperations).should().remove("queue:event:1:waiting-expiry", "old-waiting-token");
        then(zSetOperations).should(atLeastOnce()).remove("queue:user-sessions:user01", "waiting:old-waiting-token");
        then(redisTemplate).should().delete("queue:waiting-token:old-waiting-token");
    }

    @Test
    @DisplayName("유효하지 않은 waiting token으로 상태를 조회하면 예외를 던진다")
    void status_throws_exception_for_invalid_waiting_token() {
        assertThatThrownBy(() -> queueService.status(1L, "user01", "old-token"))
                .isInstanceOf(QueueTokenInvalidException.class)
                .hasMessage("올바르지 않은 시도입니다. 다시 시도해주세요.");
    }

    @Test
    @DisplayName("대기열 통과 시 Redis 스크립트로 active 슬롯 확인과 토큰 발급을 원자 처리한다")
    void status_admits_with_redis_script() {
        given(valueOperations.get("queue:waiting-token:waiting-token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-1")).willReturn((double) System.currentTimeMillis());
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(0L);
        given(zSetOperations.score(eq("queue:event:1:active"), any(String.class))).willReturn((double) System.currentTimeMillis() + 600_000);
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", "waiting-token-1");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.rank()).isZero();
        assertThat(response.token()).isNotBlank();
    }

    @Test
    @DisplayName("슬롯이 차 있으면 Redis 스크립트가 토큰 발급을 거절하고 WAITING을 유지한다")
    void status_waits_when_script_rejects_admission() {
        given(valueOperations.get("queue:waiting-token:waiting-token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-1")).willReturn((double) System.currentTimeMillis());
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", "waiting-token-1");

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.waitingCount()).isEqualTo(1L);
        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(60L);
        assertThat(response.estimatedEntryAt()).isNotNull();
        assertThat(response.activeTokenExpiresAt()).isEqualTo(response.estimatedEntryAt().plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("WAITING 응답은 현재 순번 기준 예상 입장 시간과 예상 active token 만료 시간을 제공한다")
    void status_waiting_returns_eta_fields() {
        Instant activeExpiresAt = Instant.now().plusSeconds(120);
        TypedTuple<String> activeToken = typedTuple(activeExpiresAt.toEpochMilli());
        given(valueOperations.get("queue:waiting-token:waiting-token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-1")).willReturn((double) System.currentTimeMillis());
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        given(zSetOperations.rangeWithScores("queue:event:1:active", 0, -1)).willReturn(Set.of(activeToken));
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        QueueStatusResponse response = queueService.status(1L, "user01", "waiting-token-1");

        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.estimatedEntryAt()).isBetween(activeExpiresAt.minusSeconds(1), activeExpiresAt.plusSeconds(1));
        assertThat(response.activeTokenExpiresAt()).isEqualTo(response.estimatedEntryAt().plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("enter는 사용자 세션 제한 내에서 새 대기 항목을 추가한다")
    void enter_adds_waiting_entry_when_session_limit_not_reached() {
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        given(zSetOperations.zCard("queue:user-sessions:user01")).willReturn(2L);

        QueueEnterResponse response = queueService.enter(1L, "user01", false);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.token()).isNotBlank();
        then(zSetOperations).should().add(eq("queue:user-sessions:user01"), anyString(), any(Double.class));
    }

    @Test
    @DisplayName("대기열 이탈 요청은 유효한 waiting token을 정리한다")
    void leaveWaiting_removes_valid_waiting_token() {
        given(valueOperations.get("queue:waiting-token:waiting-token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-1")).willReturn((double) System.currentTimeMillis());

        boolean left = queueService.leave(1L, "user01", "waiting-token-1", null);

        assertThat(left).isTrue();
        then(zSetOperations).should().remove("queue:event:1:waiting", "waiting-token-1");
        then(zSetOperations).should().remove("queue:event:1:waiting-expiry", "waiting-token-1");
        then(zSetOperations).should().remove("queue:user-sessions:user01", "waiting:waiting-token-1");
        then(redisTemplate).should().delete("queue:waiting-token:waiting-token-1");
    }

    @Test
    @DisplayName("validate는 유효한 active token을 검증한다")
    void validate_accepts_valid_active_token() {
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);

        var response = queueService.validate(new QueueValidateRequest(1L, "user01", "token-1"));

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("유효한 active 토큰 완료 시 active ZSet, active-token key, active-user key를 함께 정리한다")
    void complete_removes_active_token_and_user_mapping() {
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);

        boolean completed = queueService.complete(1L, "user01", "token-1");

        assertThat(completed).isTrue();
        then(zSetOperations).should().remove("queue:event:1:active", "token-1");
        then(zSetOperations).should().remove("queue:user-sessions:user01", "active:token-1");
        then(redisTemplate).should().delete("queue:active-token:token-1");
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

        assertThatThrownBy(() -> queueService.enter(1L, "user01", false))
                .isInstanceOf(DataAccessException.class)
                .hasMessage("redis error");
    }

    @Test
    @DisplayName("bulk 상태 조회는 기존 active 세션 매핑이 유효하면 READY를 유지하고 재입장시키지 않는다")
    void statuses_reuses_valid_active_user_mapping() {
        given(zSetOperations.range("queue:user-sessions:user01", 0, -1)).willReturn(Set.of("active:token-1"));
        given(zSetOperations.range("queue:user-sessions:user02", 0, -1)).willReturn(Set.of());
        given(valueOperations.get("queue:active-token:token-1")).willReturn("1:user01");
        given(valueOperations.get("queue:waiting-token:waiting-token-2")).willReturn("1:user02");
        given(zSetOperations.score("queue:event:1:active", "token-1")).willReturn((double) System.currentTimeMillis() + 600_000);
        given(zSetOperations.score("queue:event:1:waiting", "waiting-token-2")).willReturn((double) System.currentTimeMillis());
        given(zSetOperations.rank(eq("queue:event:1:waiting"), anyString())).willReturn(0L);
        given(zSetOperations.zCard("queue:event:1:waiting")).willReturn(1L);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        List<QueueStatusResponse> responses = queueAdminService.statuses(
                1L,
                List.of("user01", "user02"),
                Map.of("user02", "waiting-token-2")
        );

        assertThat(responses).extracting(QueueStatusResponse::status)
                .containsExactly("READY", "WAITING");
        assertThat(responses.get(0).token()).isEqualTo("token-1");
        assertThat(responses.get(1).rank()).isEqualTo(1L);
    }

    private TypedTuple<String> typedTuple(double score) {
        return new TypedTuple<>() {
            @Override
            public String getValue() {
                return "active-token-1";
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(TypedTuple<String> other) {
                return Double.compare(score, other.getScore());
            }
        };
    }
}
