package dev.bum.auth_service.security;

import dev.bum.common.config.LocalCorsConfig;
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

import java.util.Optional;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Optional<LocalCorsConfig> localCorsConfig;

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
                        .requestMatchers("/api/*/login", "/h2-console/**").permitAll() // 로그인과 DB 콘솔은 허용
                        .anyRequest().authenticated() // 나머지는 인증 필요
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())); // H2 콘솔 프레임 허용

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
