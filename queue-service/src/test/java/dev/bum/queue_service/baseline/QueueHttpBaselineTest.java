package dev.bum.queue_service.baseline;

import dev.bum.common.config.TokenStateConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.security.ReactiveTokenStateStore;

import com.fasterxml.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;

import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 HTTP·JWT·Redis 동작을 검증한다. 명시적으로 지정한 테스트 Redis에서만 실행한다. */
// 동기 인증 저장소는 테스트 데이터 준비에만 사용하며 실행 앱에서는 가져오지 않는다.
@Import(TokenStateConfig.class)
@AutoConfigureObservability
@EnabledIfEnvironmentVariable(named = "QUEUE_TEST_REDIS_PORT", matches = "\\d+")
@SpringBootTest(classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=baseline",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "spring.data.redis.database=2",
        "token-state.redis.host=127.0.0.1",
        "token-state.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "token-state.redis.database=0",
        "token.secret=queue-baseline-only-secret-at-least-32-bytes-long",
        "app.queue.admission-size=2",
        "app.queue.max-sessions-per-user=2",
        "management.tracing.enabled=false", "app.queue.cleanup-mode=disabled"
})
class QueueHttpBaselineTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtTokenProvider jwt;
    @Autowired TokenStateStore tokenStates;
    @Autowired StringRedisTemplate redis;
    @Autowired ReactiveStringRedisTemplate reactiveRedis;
    @Autowired ReactiveTokenStateStore reactiveTokenStates;
    private long event;
    private String user;
    private String bearer;

    @BeforeEach
    void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user = "baseline-" + UUID.randomUUID();
        bearer = credential(user);
    }

    private String credential(String id) {
        tokenStates.publish(id, new TokenState(1L, true, "ROLE_USER"));
        return jwt.createToken(id, "ROLE_USER").getAccessToken();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String auth,
                                          Map<String, String> extra, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) headers.setBearerAuth(auth);
        extra.forEach(headers::set);
        return http.exchange("/api/v1/queue" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String path(String action) { return "/events/" + event + "/" + action; }

    private String enter(String auth) {
        var response = call(HttpMethod.POST, path("enter"), auth, Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("WAITING");
        assertThat(response.getBody().path("token").asText()).isNotBlank();
        return response.getBody().path("token").asText();
    }

    private ResponseEntity<JsonNode> status(String auth, String token) {
        return call(HttpMethod.GET, path("status"), auth, Map.of("X-Waiting-Token", token), null);
    }

    @Test
    void enter_promote_validate_complete_preserves_http_and_redis_contract() {
        String waiting = enter(bearer);
        assertThat(redis.opsForValue().get("queue:waiting-token:" + waiting)).isEqualTo(event + ":" + user);
        assertThat(redis.getExpire("queue:waiting-token:" + waiting)).isPositive();
        var ready = status(bearer, waiting);
        assertThat(ready.getStatusCode().value()).isEqualTo(200);
        assertThat(ready.getBody().path("status").asText()).isEqualTo("READY");
        assertThat(ready.getBody().path("rank").asInt()).isZero();
        assertThat(ready.getBody().path("activeTokenExpiresAt").asText()).isNotBlank();
        String active = ready.getBody().path("token").asText();
        // 승격 응답이 유실되어도 동일한 대기 토큰으로 기존 활성 토큰을 복구한다.
        var replay = status(bearer, waiting);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().path("status").asText()).isEqualTo("READY");
        assertThat(replay.getBody().path("token").asText()).isEqualTo(active);
        assertThat(replay.getBody().path("activeTokenExpiresAt"))
                .isEqualTo(ready.getBody().path("activeTokenExpiresAt"));
        assertThat(redis.hasKey("queue:waiting-token:" + waiting)).isFalse();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "active:" + active)).isNotNull();
        var body = Map.of("eventId", event, "userId", user, "token", active);
        var valid = call(HttpMethod.POST, "/validate", null, Map.of(), body);
        assertThat(valid.getStatusCode().value()).isEqualTo(200);
        assertThat(valid.getBody().path("allowed").asBoolean()).isTrue();
        assertThat(valid.getBody().path("reason").asText()).isEqualTo("OK");
        assertThat(call(HttpMethod.POST, "/complete", null, Map.of(), body).getStatusCode().value()).isEqualTo(204);
        assertThat(redis.hasKey("queue:active-token:" + active)).isFalse();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":active", active)).isNull();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "active:" + active)).isNull();
        assertThat(call(HttpMethod.POST, "/complete", null, Map.of(), body).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/validate", null, Map.of(), body).getBody().path("allowed").asBoolean()).isFalse();
        // 완료된 세션의 대기 토큰은 재조회로 되살리지 않는다.
        assertThat(status(bearer, waiting).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void waiting_and_active_leave_return_204_and_remove_session() {
        String waiting = enter(bearer);
        assertThat(call(HttpMethod.POST, path("leave"), bearer,
                Map.of("X-Waiting-Token", waiting), null).getStatusCode().value()).isEqualTo(204);
        assertThat(redis.hasKey("queue:waiting-token:" + waiting)).isFalse();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "waiting:" + waiting)).isNull();
        String active = status(bearer, enter(bearer)).getBody().path("token").asText();
        assertThat(call(HttpMethod.POST, path("leave"), bearer,
                Map.of("X-Active-Token", active), null).getStatusCode().value()).isEqualTo(204);
        assertThat(redis.hasKey("queue:active-token:" + active)).isFalse();
    }

    @Test
    void session_limit_requires_confirmation_and_force_replaces_oldest_session() {
        String first = enter(bearer);
        enter(bearer);
        var limited = call(HttpMethod.POST, path("enter"), bearer, Map.of(), null);
        assertThat(limited.getStatusCode().value()).isEqualTo(200);
        assertThat(limited.getBody().path("status").asText()).isEqualTo("SESSION_LIMIT_CONFIRM_REQUIRED");
        var forced = call(HttpMethod.POST, path("enter"), bearer, Map.of("X-Queue-Force-Enter", "true"), null);
        assertThat(forced.getBody().path("status").asText()).isEqualTo("WAITING");
        assertThat(redis.hasKey("queue:waiting-token:" + first)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }

    @Test
    void invalid_or_foreign_waiting_token_returns_standard_error() {
        String waiting = enter(bearer);
        String other = credential("baseline-" + UUID.randomUUID());
        for (String token : List.of(waiting, "missing-token")) {
            var response = status(other, token);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().path("code").asText()).isEqualTo("QUEUE_TOKEN_INVALID");
        }
        assertThat(redis.hasKey("queue:waiting-token:" + waiting)).isTrue();
    }

    @Test
    void invalid_request_body_returns_validation_error_without_authentication() {
        for (String endpoint : List.of("/validate", "/complete")) {
            var response = call(HttpMethod.POST, endpoint, null, Map.of(), Map.of());
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().path("code").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(response.getBody().path("details").has("eventId")).isTrue();
        }
    }

    @Test
    void authentication_rejects_missing_revoked_refresh_and_malformed_tokens() {
        assertThat(call(HttpMethod.POST, path("enter"), null, Map.of(), null).getStatusCode().value()).isEqualTo(403);
        var refresh = jwt.createToken(user, "ROLE_USER").getRefreshToken();
        assertThat(call(HttpMethod.POST, path("enter"), refresh, Map.of(), null).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, path("enter"), "malformed", Map.of(), null).getStatusCode().value()).isEqualTo(403);
        tokenStates.publish(user, new TokenState(2L, false, "ROLE_USER"));
        var revoked = call(HttpMethod.POST, path("enter"), bearer, Map.of(), null);
        assertThat(revoked.getStatusCode().value()).isEqualTo(401);
        assertThat(revoked.getBody().path("error").asText()).isEqualTo("인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요.");
    }

    @Test
    void expired_waiting_token_is_rejected_and_removed_from_event_indexes() {
        String waiting = enter(bearer);
        redis.expire("queue:waiting-token:" + waiting, Duration.ofMillis(1));
        redis.opsForZSet().add("queue:event:" + event + ":waiting-expiry", waiting, 0);
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> !Boolean.TRUE.equals(redis.hasKey("queue:waiting-token:" + waiting)));
        assertThat(status(bearer, waiting).getStatusCode().value()).isEqualTo(400);
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting", waiting)).isNull();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", waiting)).isNull();
    }

    @Test
    void normal_user_cannot_call_management_api() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        var response = http.exchange("/api/v1/manage/queue/test/events/" + event + "/enter?userId=" + user,
                HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void reactive_queue_operations_share_the_existing_queue_database_and_support_lua() {
        String key = "baseline:reactive:" + UUID.randomUUID();
        StepVerifier.create(reactiveRedis.opsForValue()
                .set(key, "queue-value", Duration.ofSeconds(30)))
                .expectNext(true).verifyComplete();
        assertThat(redis.opsForValue().get(key)).isEqualTo("queue-value");
        var script = new DefaultRedisScript<>(
                "return redis.call('GET', KEYS[1])", String.class);
        StepVerifier.create(reactiveRedis.execute(script, List.of(key)))
                .expectNext("queue-value").verifyComplete();
        StepVerifier.create(reactiveRedis.delete(key)).expectNext(1L).verifyComplete();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void reactive_authentication_reads_auth_database_and_observes_revocation() {
        // 대기열 DB에 같은 키가 있어도 별도 인증 DB의 상태를 조회해야 한다.
        String key = TokenStateStore.key(user);
        redis.opsForValue().set(key, "99:0:ROLE_ADMIN", Duration.ofSeconds(30));
        StepVerifier.create(reactiveTokenStates.get(user))
                .expectNext(new TokenState(1L, true, "ROLE_USER")).verifyComplete();
        tokenStates.publish(user, new TokenState(2L, false, "ROLE_USER"));
        StepVerifier.create(reactiveTokenStates.get(user))
                .expectNext(new TokenState(2L, false, "ROLE_USER")).verifyComplete();
        assertThat(redis.opsForValue().get(key)).isEqualTo("99:0:ROLE_ADMIN");
        redis.delete(key);
    }

    @Test
    void reactive_authentication_completes_empty_for_unknown_user() {
        StepVerifier.create(reactiveTokenStates.get("baseline-missing-" + UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void admin_management_routes_use_reactive_services_and_preserve_responses() {
        String adminId = "baseline-admin-" + UUID.randomUUID();
        tokenStates.publish(adminId, new TokenState(1L, true, "ROLE_ADMIN"));
        String admin = jwt.createToken(adminId, "ROLE_ADMIN").getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(admin);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String base = "/api/v1/manage/queue";
        String testEvent = base + "/test/events/" + event;
        var waiting = http.exchange(testEvent + "/enter?userId=" + user,
                HttpMethod.POST, new HttpEntity<>(null, headers), JsonNode.class);
        assertThat(waiting.getStatusCode().value()).isEqualTo(200);
        String token = waiting.getBody().path("token").asText();
        var inspection = http.exchange(base + "/redis/event/" + event,
                HttpMethod.GET, new HttpEntity<>(null, headers), JsonNode.class);
        assertThat(inspection.getStatusCode().value()).isEqualTo(200);
        assertThat(inspection.getBody().path("entries").get(0).path("token").asText()).isEqualTo(token);
        var bulk = http.exchange(testEvent + "/statuses", HttpMethod.POST,
                new HttpEntity<>(Map.of("userIds", List.of(user), "tokenByUserId", Map.of(user, token)), headers),
                JsonNode.class);
        assertThat(bulk.getStatusCode().value()).isEqualTo(200);
        String active = bulk.getBody().get(0).path("token").asText();
        assertThat(bulk.getBody().get(0).path("status").asText()).isEqualTo("READY");
        var inspectedToken = http.exchange(base + "/redis/token/" + active, HttpMethod.GET,
                new HttpEntity<>(null, headers), JsonNode.class);
        assertThat(inspectedToken.getBody().path("entries").get(0).path("value").asText()).isEqualTo(event + ":" + user);
        var removed = http.exchange(base + "/redis/event/" + event + "/token/" + active + "?mode=ACTIVE",
                HttpMethod.DELETE, new HttpEntity<>(null, headers), String.class);
        assertThat(removed.getStatusCode().value()).isEqualTo(204);
        assertThat(http.exchange(base + "/redis/event/" + event + "/token/" + active + "?mode=ACTIVE",
                HttpMethod.DELETE, new HttpEntity<>(null, headers), String.class).getStatusCode().value()).isEqualTo(404);
        String another = http.exchange(testEvent + "/enter?userId=" + user, HttpMethod.POST,
                new HttpEntity<>(null, headers), JsonNode.class).getBody().path("token").asText();
        var ready = http.exchange(testEvent + "/status?userId=" + user + "&token=" + another, HttpMethod.GET,
                new HttpEntity<>(null, headers), JsonNode.class);
        String nextActive = ready.getBody().path("token").asText();
        var completed = http.exchange(testEvent + "/complete?userId=" + user + "&token=" + nextActive,
                HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(200);
        assertThat(completed.getBody()).isEqualTo("대기열 active 토큰을 완료 처리했습니다.");
        assertThat(http.exchange(testEvent + "/complete?userId=" + user + "&token=" + nextActive,
                HttpMethod.POST, new HttpEntity<>(null, headers), JsonNode.class).getBody()
                .path("code").asText()).isEqualTo("QUEUE_TOKEN_INVALID");
        http.exchange(testEvent + "/enter?userId=" + user, HttpMethod.POST,
                new HttpEntity<>(null, headers), JsonNode.class);
        var cleared = http.exchange(testEvent, HttpMethod.DELETE, new HttpEntity<>(null, headers), String.class);
        assertThat(cleared.getStatusCode().value()).isEqualTo(200);
        assertThat(cleared.getBody()).contains("Redis key 1건");
        assertThat(redis.hasKey("queue:event:" + event + ":waiting")).isFalse();
    }

    @Test
    void valid_jwt_with_missing_authentication_state_is_revoked() {
        String unknown = jwt.createToken("baseline-unknown-" + UUID.randomUUID(), "ROLE_USER").getAccessToken();
        var response = call(HttpMethod.POST, path("enter"), unknown, Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().path("error").asText()).isEqualTo("인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요.");
    }
    @Test
    void reactive_http_and_service_observations_are_exported_to_prometheus() {
        enter(bearer);
        var response = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("queue_enter_seconds_count", "http_server_requests_seconds_count");
    }
    @Test
    void concurrent_status_calls_do_not_exceed_admission_capacity() throws Exception {
        int clients = 12;
        List<String> credentials = new ArrayList<>();
        List<String> waiting = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            String auth = credential("baseline-" + UUID.randomUUID());
            credentials.add(auth);
            waiting.add(enter(auth));
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(clients)) {
            List<Future<ResponseEntity<JsonNode>>> pending = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                final int index = i;
                pending.add(executor.submit(() -> {
                    start.await();
                    return status(credentials.get(index), waiting.get(index));
                }));
            }
            start.countDown();
            int ready = 0;
            for (var future : pending) {
                var response = future.get(15, TimeUnit.SECONDS);
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                String state = response.getBody().path("status").asText();
                assertThat(state).isIn("READY", "WAITING");
                if (state.equals("READY")) ready++;
                else {
                    assertThat(response.getBody().path("rank").asLong()).isPositive();
                    assertThat(response.getBody().path("estimatedEntryAt").asText()).isNotBlank();
                }
            }
            assertThat(ready).isEqualTo(2);
            assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(2L);
            assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":waiting")).isEqualTo(10L);
        }
    }
}
