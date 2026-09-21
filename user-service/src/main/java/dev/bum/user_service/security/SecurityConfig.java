package dev.bum.user_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Bean
    @Order(1)
    public SecurityFilterChain publicUserFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/api/*/signup",
                        "/api/*/check/duplication/**",
                        "/api/*/find/id/**",
                        "/api/*/find/password/**",
                        "/api/*/reset/password"
                )
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // REST API이므로 CSRF 비활성화
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용
                .authorizeHttpRequests(auth -> auth
                        // 1. 공통 인프라 통로 개방
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()

                        // 2. 비로그인 유저(전체) 허용: 로그인, 회원가입, 중복 검사
                        .requestMatchers("/api/*/check/duplication/**").permitAll()
                        .requestMatchers("/api/*/signup").permitAll()

                        // 3. 관리자(ADMIN) 및 유저(USER) 모두 접근 가능 (내 정보 조회 / 내 정보 수정)
                        .requestMatchers("/api/*/select/me").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/update/me").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/withdraw/me").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/validate/info").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/address/insert/me").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/address/select/me").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/address/update/me/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/address/delete/me/**").hasAnyRole(ROLE_ADMIN_OR_USER)
                        .requestMatchers("/api/*/manage/**").hasRole(ROLE_ADMIN)

                        // 4. 나머지 모든 요청은 무조건 관리자(ADMIN)만 가능
                        .anyRequest().hasRole(ROLE_ADMIN)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 회원가입 시 비번 암호화 & 로그인 시 대조용
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // 로그인 로직에서 인증을 시도할 매니저
    }
}
