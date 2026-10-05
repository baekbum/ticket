package dev.bum.support_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.config.TokenStateConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Import({JwtTokenProvider.class, TokenStateConfig.class})
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStateStore tokenStateStore;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/*/notice/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/*/faq/**").permitAll()
                        .requestMatchers("/api/*/manage/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, tokenStateStore), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
