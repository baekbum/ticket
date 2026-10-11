package dev.bum.queue_service;

import dev.bum.common.security.TokenStateStore;
import dev.bum.queue_service.scheduler.QueueCleanupScheduler;
import dev.bum.queue_service.scheduler.ReactiveQueueCleanupScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.netty.NettyWebServer;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local", "app.queue.cleanup-mode=disabled",
        "spring.task.scheduling.pool.size=1"
})
class QueueServiceApplicationTests {
    @Autowired ApplicationContext context;
    @LocalServerPort int port;
    private WebTestClient http;

    @BeforeEach
    void httpClient() {
        // 상대 주소 기반의 모의 요청 대신 실제 Netty 서버로 사전 요청을 전송한다.
        http = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void netty_server_does_not_load_blocking_authentication_store() {
        assertThat(context).isInstanceOf(ReactiveWebServerApplicationContext.class);
        assertThat(((ReactiveWebServerApplicationContext) context).getWebServer()).isInstanceOf(NettyWebServer.class);
        assertThat(context.getBeansOfType(ReactiveQueueCleanupScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(QueueCleanupScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(TokenStateStore.class)).isEmpty();
    }

    @Test
    void local_cors_preflight_preserves_browser_origins_and_headers() {
        http.options().uri("/api/v1/queue/events/1/enter")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,X-Queue-Force-Enter")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        http.options().uri("/api/v1/queue/events/1/enter")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange().expectStatus().isForbidden();
    }
}
