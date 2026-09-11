package dev.bum.client_api_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.HeaderAuthenticationFilter;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    @Value("${spring.profiles.default:local}")
    private String activeProfile;

    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/health", "/actuator/prometheus")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                    localCorsConfig.ifPresent(config ->
                            cors.configurationSource(config.corsConfigurationSource())
                    );

                    if (localCorsConfig.isEmpty()) {
                        cors.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain publicEventFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/*/event/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                    localCorsConfig.ifPresent(config ->
                            cors.configurationSource(config.corsConfigurationSource())
                    );

                    if (localCorsConfig.isEmpty()) {
                        cors.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/*/event/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain publicUserFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/api/*/user/signup",
                        "/api/*/user/check/duplication/**",
                        "/api/*/user/find/id/**",
                        "/api/*/user/find/password/**",
                        "/api/*/user/reset/password"
                )
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                    localCorsConfig.ifPresent(config ->
                            cors.configurationSource(config.corsConfigurationSource())
                    );

                    if (localCorsConfig.isEmpty()) {
                        cors.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                    localCorsConfig.ifPresent(config ->
                            cors.configurationSource(config.corsConfigurationSource())
                    );

                    if (localCorsConfig.isEmpty()) {
                        cors.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/*/auth/login").permitAll()
                        .requestMatchers("/api/*/auth/reissue").permitAll()
                        .requestMatchers("/api/*/auth/logout").permitAll()
                        .requestMatchers("/api/*/user/signup").permitAll()
                        .requestMatchers("/api/*/user/check/duplication/**").permitAll()
                        .anyRequest().authenticated()
                );

        if ("local".equals(activeProfile)) {
            http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        } else {
            http.addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
