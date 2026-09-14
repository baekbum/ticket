package dev.bum.queue_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.HeaderAuthenticationFilter;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String[] ROLE_ADMIN_OR_USER = {"ADMIN", "USER"};

    @Value("${spring.profiles.default:local}")
    private String activeProfile;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/v1/queue/validate").permitAll()
                        .requestMatchers("/api/v1/queue/complete").permitAll()
                        .requestMatchers("/api/v1/manage/queue/**").hasRole(ROLE_ADMIN)
                        .requestMatchers("/api/v1/queue/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .anyRequest().hasRole(ROLE_ADMIN)
                );

        configureAuthenticationFilter(http);

        return http.build();
    }

    private void configureAuthenticationFilter(HttpSecurity http) {
        if ("local".equals(activeProfile)) {
            http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        } else {
            http.addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }
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
