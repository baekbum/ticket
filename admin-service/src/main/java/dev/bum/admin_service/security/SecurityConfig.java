package dev.bum.admin_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.HeaderAuthenticationFilter;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    // 🌟 application.yml의 spring.profiles.default 값을 읽어옵니다. (없으면 local)
    @Value("${spring.profiles.default:local}")
    private String activeProfile;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // REST API이므로 CSRF 비활성화
                .cors(cors -> {
                    localCorsConfig.ifPresent(config ->
                            cors.configurationSource(config.corsConfigurationSource())
                    );

                    if (localCorsConfig.isEmpty()) {
                        cors.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용
                .authorizeHttpRequests(auth -> auth
                        // 로그인 화면 및 로그인 시도는 허용
                        .requestMatchers("/api/*/view/login").permitAll()
                        .requestMatchers("/api/*/auth/login").permitAll()

                        // 로그인 성공 시 대시보드 화면 및 fragment 화면 허용
                        .requestMatchers("/api/*/view/home").permitAll()
                        .requestMatchers("/api/*/view/fragment/**").permitAll()

                        // 정적 리소스(CSS, JS)가 시큐리티에 막혀 화면이 깨지는 것을 방지
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()

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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 회원가입 시 비번 암호화 & 로그인 시 대조용
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // 로그인 로직에서 인증을 시도할 매니저
    }
}
