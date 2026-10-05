package dev.bum.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    @Test
    void both_tokens_carry_current_version_and_expected_lifetime() {
        String secret = "test-secret-for-refresh-token-uniqueness-01234567890123456789";
        JwtTokenProvider provider = new JwtTokenProvider(secret, 900000L, 1209600000L);
        var tokens = provider.createToken("user", "ROLE_USER", 7L);
        for (String token : new String[]{tokens.getAccessToken(), tokens.getRefreshToken()}) {
            var claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build().parseClaimsJws(token).getBody();
            assertThat(provider.getTokenVersion(token)).isEqualTo(7L);
            long ttl = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(ttl).isEqualTo(token.equals(tokens.getAccessToken()) ? 900000L : 1209600000L);
        }
        assertThat(provider.getTokenType(tokens.getAccessToken())).isEqualTo("access");
        assertThat(provider.getTokenType(tokens.getRefreshToken())).isEqualTo("refresh");
    }

    @Test
    @DisplayName("동일 사용자의 연속 발급에도 Refresh Token의 jti는 매번 다르다")
    void refresh_tokens_have_unique_ids() {
        String secret = "test-secret-for-refresh-token-uniqueness-01234567890123456789";
        JwtTokenProvider provider = new JwtTokenProvider(secret, 900000L, 1209600000L);
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = provider.createToken("user", "ROLE_USER").getRefreshToken();
            var claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build().parseClaimsJws(token).getBody();
            assertThat(claims.getSubject()).isEqualTo("user");
            assertThat(claims.getId()).isNotBlank();
            ids.add(claims.getId());
            tokens.add(token);
        }
        assertThat(ids).hasSize(100);
        assertThat(tokens).hasSize(100);
    }
}
