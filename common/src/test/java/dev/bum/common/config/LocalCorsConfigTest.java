package dev.bum.common.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCorsConfigTest {

    private final CorsFilter filter = new CorsFilter(new LocalCorsConfig().corsConfigurationSource());

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "https://127.0.0.1", "https://[::1]",
            "http://localhost:3000", "http://localhost:8999"})
    void allowsLoginThroughHttpBackendFromLocalOrigins(String origin) throws Exception {
        MockHttpServletRequest request = loginRequest("POST", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedLogin = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> reachedLogin.set(true));

        assertThat(reachedLogin).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "http://localhost:8999"})
    void allowsLoginPreflight(String origin) throws Exception {
        MockHttpServletRequest request = loginRequest("OPTIONS", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Preflight must be handled by the CORS filter");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "https://localhost:444", "https://localhost.example.com"})
    void rejectsUnapprovedOrigins(String origin) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(loginRequest("POST", origin), response, (req, res) -> {
            throw new AssertionError("Unapproved origin must not reach login");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private MockHttpServletRequest loginRequest(String method, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/admin/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", origin);
        request.addHeader("X-Forwarded-Proto", origin.startsWith("https:") ? "https" : "http");
        return request;
    }
}
