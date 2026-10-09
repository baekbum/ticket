package dev.bum.queue_service.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import dev.bum.queue_service.QueueServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP + JWT + Redis contract. Run only against an explicitly supplied test Redis. */
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
        "management.tracing.enabled=false"
})
class QueueHttpBaselineTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtTokenProvider jwt;
    @Autowired TokenStateStore tokenStates;
    @Autowired StringRedisTemplate redis;
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
        // Current behavior: promotion consumes the waiting token; replay is not idempotent.
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
        assertThat(revoked.getBody().path("error").asText()).isEqualTo("Token Revoked");
    }

    @Test
    void expired_waiting_token_is_rejected_and_removed_from_event_indexes() {
        String waiting = enter(bearer);
        redis.expire("queue:waiting-token:" + waiting, java.time.Duration.ofMillis(1));
        redis.opsForZSet().add("queue:event:" + event + ":waiting-expiry", waiting, 0);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3))
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
