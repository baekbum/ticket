package dev.bum.common.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Key key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    // 생성자에서 주입받아 처리하면 final 키워드를 유지할 수 있습니다.
    public JwtTokenProvider(
            @Value("${token.secret}") String secret,
            @Value("${token.accessTokenValidityInMilliseconds}") long accessValidity,
            @Value("${token.refreshTokenValidityInMilliseconds}") long refreshValidity) {

        log.info("token.secret : {}", secret);
        log.info("token.validity : {}", accessValidity);

        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidityInMilliseconds = accessValidity;
        this.refreshTokenValidityInMilliseconds = refreshValidity;
    }

    // 1. 토큰 생성
    public dev.bum.common.dto.TokenDto createToken(String userId, String role) {
        Claims claims = Jwts.claims().setSubject(userId);
        claims.put("auth", role); // 권한 정보 추가

        Date now = new Date();
        Date accessValidity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        // 1. Access Token 생성
        String accessToken = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(accessValidity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // 2. Refresh Token 생성
        Date refreshValidity = new Date(now.getTime() + refreshTokenValidityInMilliseconds);

        String refreshToken = Jwts.builder()
                .setExpiration(refreshValidity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.info("accessToken : {}", accessToken);
        log.info("refreshToken : {}", refreshToken);

        return new dev.bum.common.dto.TokenDto(accessToken, refreshToken);
    }

    // 2. 토큰에서 인증 정보 조회 (SecurityContext에 저장하기 위함)
    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    // 3. 토큰의 유효성 + 만료일자 확인
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }
}
