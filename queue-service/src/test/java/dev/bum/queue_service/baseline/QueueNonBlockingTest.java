package dev.bum.queue_service.baseline;

import dev.bum.common.config.TokenStateConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.service.reactive.ReactiveQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "QUEUE_TEST_REDIS_PORT", matches = "\\d+")
@Import(TokenStateConfig.class)
@SpringBootTest(classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=baseline",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "spring.data.redis.database=4",
        "token-state.redis.host=127.0.0.1", "token-state.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "token-state.redis.database=0",
        "token.secret=queue-baseline-only-secret-at-least-32-bytes-long",
        "app.queue.admission-size=2", "app.queue.max-sessions-per-user=2",
        "app.queue.cleanup-mode=disabled", "management.tracing.enabled=false"
})
class QueueNonBlockingTest {
    static { BlockHound.install(); }

    @LocalServerPort int port;
    @Autowired JwtTokenProvider jwt;
    @Autowired TokenStateStore tokenStates;
    @Autowired ReactiveQueueService queue;

    @Test
    void detector_rejects_blocking_on_a_reactor_thread() {
        StepVerifier.create(Mono.fromCallable(() -> { Thread.sleep(10); return true; })
                        .subscribeOn(Schedulers.parallel()))
                .expectError(BlockingOperationError.class).verify(Duration.ofSeconds(10));
    }

    @Test
    void actual_http_authentication_registration_promotion_and_completion_are_non_blocking() {
        String user = "nonblocking-" + UUID.randomUUID();
        long event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        tokenStates.publish(user, new TokenState(1L, true, "ROLE_USER"));
        String bearer = "Bearer " + jwt.createToken(user, "ROLE_USER").getAccessToken();
        var client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(20)).defaultHeader("Authorization", bearer).build();
        Map<?, ?> entered = client.post().uri("/api/v1/queue/events/" + event + "/enter")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Map<?, ?> ready = client.get().uri("/api/v1/queue/events/" + event + "/status")
                .header("X-Waiting-Token", (String) entered.get("token"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(ready.get("status")).isEqualTo("READY");
        Map<?, ?> second = client.post().uri("/api/v1/queue/events/" + event + "/enter")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Map<?, ?> secondReady = client.get().uri("/api/v1/queue/events/" + event + "/status")
                .header("X-Waiting-Token", (String) second.get("token"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(secondReady.get("status")).isEqualTo("READY");
        String otherUser = user + "-other";
        tokenStates.publish(otherUser, new TokenState(1L, true, "ROLE_USER"));
        String otherBearer = "Bearer " + jwt.createToken(otherUser, "ROLE_USER").getAccessToken();
        Map<?, ?> other = client.post().uri("/api/v1/queue/events/" + event + "/enter")
                .header("Authorization", otherBearer).exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        client.get().uri("/api/v1/queue/events/" + event + "/status")
                .header("Authorization", otherBearer).header("X-Waiting-Token", (String) other.get("token"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WAITING");
        client.post().uri("/api/v1/queue/events/" + event + "/leave")
                .header("Authorization", otherBearer).header("X-Waiting-Token", (String) other.get("token"))
                .exchange().expectStatus().isNoContent();
        client.post().uri("/api/v1/queue/complete")
                .bodyValue(Map.of("eventId", event, "userId", user, "token", ready.get("token")))
                .exchange().expectStatus().isNoContent();
        StepVerifier.create(queue.cleanupExpiredTokens().subscribeOn(Schedulers.parallel()))
                .verifyComplete();
    }
}