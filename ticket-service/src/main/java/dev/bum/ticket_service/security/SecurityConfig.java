package dev.bum.ticket_service.security;

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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
    private static final String[] ROLE_ADMIN_OR_USER = {"ADMIN", "USER"};
    private static final String ROLE_INTERNAL = "INTERNAL_SERVICE";

    @Value("${spring.profiles.default:local}")
    private String activeProfile;

    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/uploads/**", "/actuator/health", "/actuator/prometheus", "/h2-console/**")
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain publicEventFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/*/event/**")
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/*/event/**").permitAll()
                        .anyRequest().hasAnyRole(ROLE_ADMIN_OR_USER)
                );

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/*/payments/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().hasRole(ROLE_INTERNAL)
                );

        http.addFilterBefore(
                new InternalServiceAuthenticationFilter(internalServiceTokenValidator),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 2. 관리자용 통로
                        .requestMatchers("/api/*/manage/**").hasRole(ROLE_ADMIN)

                        // 3. 사용자용 통로
                        .requestMatchers("/api/*/coupon/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/event/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/area/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/seat/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/checkout/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/reservation/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/ticket/**").hasAnyRole(ROLE_ADMIN_OR_USER)

                        // 나머지 모든 요청은 무조건 관리자(ADMIN)만 가능
                        .anyRequest().hasRole(ROLE_ADMIN)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

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

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // 로그인 로직에서 인증을 시도할 매니저
    }
}
