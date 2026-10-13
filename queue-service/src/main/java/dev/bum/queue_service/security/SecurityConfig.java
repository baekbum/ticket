package dev.bum.queue_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveTokenStateStore tokenStateStore;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, ObjectProvider<CorsConfigurationSource> corsSources) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                // 기존처럼 인증 정보가 없는 보호 경로는 403으로 응답한다.
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusServerEntryPoint(HttpStatus.FORBIDDEN)))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers("/api/v1/queue/validate", "/api/v1/queue/complete").permitAll()
                        .pathMatchers("/api/v1/manage/queue/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/queue/**").hasAnyRole("ADMIN", "USER")
                        .anyExchange().hasRole("ADMIN"));
        CorsConfigurationSource corsSource = corsSources.getIfAvailable();
        if (corsSource == null) {
            http.cors(ServerHttpSecurity.CorsSpec::disable);
        } else {
            http.cors(cors -> cors.configurationSource(corsSource));
        }
        // 전역 WebFilter 빈으로 등록하지 않아 인증 필터가 두 번 실행되지 않도록 한다.
        http.addFilterAt(new ReactiveJwtAuthenticationFilter(jwtTokenProvider, tokenStateStore),
                SecurityWebFiltersOrder.AUTHENTICATION);
        return http.build();
    }
}
