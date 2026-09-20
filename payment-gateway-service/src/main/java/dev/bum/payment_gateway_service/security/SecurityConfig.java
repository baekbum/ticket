package dev.bum.payment_gateway_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Optional<LocalCorsConfig> localCorsConfig;
    private final JwtTokenProvider jwtTokenProvider;
    private final InternalServiceTokenValidator internalServiceTokenValidator;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_INTERNAL = "INTERNAL_SERVICE";
    private static final String[] ROLE_ADMIN_USER_OR_INTERNAL = {"ADMIN", "USER", ROLE_INTERNAL};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/*/payments/card/**").hasAnyRole(ROLE_ADMIN_USER_OR_INTERNAL)
                        .requestMatchers("/api/*/payments/virtual-account/**").hasAnyRole(ROLE_ADMIN_USER_OR_INTERNAL)
                        .anyRequest().hasRole(ROLE_ADMIN)
                );

        configureAuthenticationFilters(http);

        return http.build();
    }

    private void configureAuthenticationFilters(HttpSecurity http) {
        JwtAuthenticationFilter clientAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        // 일반 인증 필터를 기준점으로 등록한 뒤 내부 서비스 필터가 먼저 실행되도록 순서를 고정한다.
        http.addFilterBefore(clientAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(
                new InternalServiceAuthenticationFilter(internalServiceTokenValidator),
                clientAuthenticationFilter.getClass()
        );
    }

    private void configureCors(CorsConfigurer<HttpSecurity> cors) {
        localCorsConfig.ifPresent(config ->
                cors.configurationSource(config.corsConfigurationSource())
        );

        if (localCorsConfig.isEmpty()) {
            cors.disable();
        }
    }
}
