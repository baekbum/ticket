package dev.bum.ticket_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.HeaderAuthenticationFilter;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    @Value("${spring.profiles.default:local}")
    private String activeProfile;

    @Bean
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
                        // 1. 공통 인프라 통로 개방
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // 2. 관리자용 통로
                        .requestMatchers("/api/*/manage/**").hasRole("ADMIN")

                        // 3. 사용자용 통로
                        .requestMatchers("/api/*/coupon/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/*/event/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/*/area/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/*/seat/**").hasAnyRole("USER", "ADMIN")

                        // 나머지 모든 요청은 무조건 관리자(ADMIN)만 가능
                        .anyRequest().hasRole("ADMIN")
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        // =================================================================
        // 🌟 [핵심 변경] 실행 환경(Profile)에 따른 필터 자동 교체 스위치
        // =================================================================
        if ("local".equals(activeProfile)) {
            // 로컬 개발 환경: 인그레스 없이 직접 포트로 접근하므로 토큰을 직접 복호화하는 기존 필터 작동
            // (JwtAuthenticationFilter 패키지 경로가 다르면 import를 맞춰주세요)
            http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        } else {
            // 운영/쿠버네티스 환경: Nginx와 auth-service가 검증 후 밀어 넣어준 헤더를 기반으로 신뢰 작동
            http.addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // 로그인 로직에서 인증을 시도할 매니저
    }
}
