package dev.bum.common.security;

import dev.bum.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
            "token-state-test-secret-012345678901234567890123456789", 900000L, 1209600000L);
    private final TokenStateStore store = mock(TokenStateStore.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void current_access_token_authenticates() throws Exception {
        when(store.get("user")).thenReturn(TokenState.builder().version(3L).active(true).role("ROLE_USER").build());
        assertThat(request(provider.createToken("user", "ROLE_USER", 3L).getAccessToken())).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user");
        verify(chain).doFilter(any(), any());
    }

    @Test void password_change_revokes_unexpired_access_token() throws Exception {
        String token = provider.createToken("user", "ROLE_USER", 1L).getAccessToken();
        assertThat(provider.validateToken(token)).isTrue();
        when(store.get("user")).thenReturn(TokenState.builder().version(2L).active(true).role("ROLE_USER").build());
        assertThat(request(token)).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test void blocked_account_is_rejected() throws Exception {
        when(store.get("user")).thenReturn(TokenState.builder().version(1L).active(false).role("ROLE_USER").build());
        assertThat(request(provider.createToken("user", "ROLE_USER", 1L).getAccessToken())).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test void previous_admin_authority_is_rejected() throws Exception {
        when(store.get("user")).thenReturn(TokenState.builder().version(2L).active(true).role("ROLE_USER").build());
        assertThat(request(provider.createToken("user", "ROLE_ADMIN", 1L).getAccessToken())).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test void missing_state_is_rejected() throws Exception {
        assertThat(request(provider.createToken("user", "ROLE_USER", 1L).getAccessToken())).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test void redis_outage_does_not_allow_authentication() throws Exception {
        when(store.get("user")).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThat(request(provider.createToken("user", "ROLE_USER", 1L).getAccessToken())).isEqualTo(503);
        verifyNoInteractions(chain);
    }

    @Test void refresh_token_cannot_be_used_as_access_token() throws Exception {
        assertThat(request(provider.createToken("user", "ROLE_USER", 1L).getRefreshToken())).isEqualTo(401);
        verifyNoInteractions(chain, store);
    }

    @Test void legacy_token_without_type_is_rejected() throws Exception {
        String token = io.jsonwebtoken.Jwts.builder().setSubject("user").claim("auth", "ROLE_USER")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 900000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "token-state-test-secret-012345678901234567890123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        assertThat(request(token)).isEqualTo(401);
        verifyNoInteractions(chain, store);
    }

    private int request(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(provider, store).doFilter(request, response, chain);
        return response.getStatus();
    }
}
