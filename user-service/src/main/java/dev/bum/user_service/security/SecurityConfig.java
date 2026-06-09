package dev.bum.user_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
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
                        // 1. 공통 인프라 통로 개방
                        .requestMatchers("/h2-console/**").permitAll()

                        // 2. 비로그인 유저(전체) 허용: 로그인, 회원가입, 중복 검사
                        .requestMatchers("/api/v1/check/duplication/**").permitAll()
                        .requestMatchers("/api/v1/insert").permitAll()

                        // 3. 관리자(ADMIN) 및 유저(USER) 모두 접근 가능 (내 정보 조회 / 내 정보 수정)
                        .requestMatchers("/api/v1/select/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/update/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/validate/info").hasAnyRole("USER", "ADMIN")

                        // 4. 나머지 모든 요청은 무조건 관리자(ADMIN)만 가능
                        // (selectAll, selectByCond, selectById, update, delete 등)
                        .anyRequest().hasRole("ADMIN")
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                // [핵심] 필터 순서 지정: Jwt 필터를 인증 필터보다 먼저 실행!
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // 로그인 로직에서 인증을 시도할 매니저
    }
}
