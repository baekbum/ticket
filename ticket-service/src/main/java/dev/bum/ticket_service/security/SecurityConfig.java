package dev.bum.ticket_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()

                        // 1. Event 권한 설정
                        .requestMatchers(HttpMethod.GET, "/api/v1/event/**").hasAnyRole("USER", "ADMIN") // Read는 둘 다
                        .requestMatchers("/api/v1/event/**").hasRole("ADMIN") // 나머지는 ADMIN만

                        // 2. Seat 권한 설정
                        .requestMatchers(HttpMethod.GET, "/api/v1/seat/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/seat/**").hasRole("ADMIN")

                        // 3. Reservation 권한 설정 (일반적으로 생성/취소는 유저 본인도 가능해야 함)
                        .requestMatchers("/api/v1/reservation/**").hasAnyRole("USER", "ADMIN")

                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

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
