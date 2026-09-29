package dev.bum.payment_gateway_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    private final PaymentProviderTokenValidator paymentProviderTokenValidator;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_INTERNAL = "INTERNAL_SERVICE";
    private static final String ROLE_PAYMENT_PROVIDER = "PAYMENT_PROVIDER";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/*/payments/card/approve").hasRole(ROLE_USER) // 카드 결제 최소 승인
                        .requestMatchers(HttpMethod.GET, "/api/*/payments/card/internal/*/approval-exists").hasRole(ROLE_INTERNAL) // 승인 내용이 있는지 조회
                        .requestMatchers(HttpMethod.GET, "/api/*/payments/card/*").hasRole(ROLE_USER) // 카드 상태 조회
                        .requestMatchers(HttpMethod.POST, "/api/*/payments/card/refund").hasRole(ROLE_INTERNAL) // 환불의 경우 ticket 서비스에서 요청
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/*/payments/virtual-account/issue",
                                "/api/*/payments/virtual-account/refund"
                        ).hasRole(ROLE_INTERNAL) // 가상 계좌 발급 또는 결제 금액 환불의 경우 ticket 서비스를 통해 이루어짐
                        .requestMatchers(HttpMethod.POST, "/api/*/payments/virtual-account/deposit").hasRole(ROLE_PAYMENT_PROVIDER) // 무통장 입금 케이스
                        .anyRequest().denyAll()
                );

        configureAuthenticationFilters(http);

        return http.build();
    }

    private void configureAuthenticationFilters(HttpSecurity http) {
        JwtAuthenticationFilter clientAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        // 일반 인증 필터를 기준점으로 등록한 뒤 내부 서비스 필터가 먼저 실행되도록 순서를 고정한다.
        http.addFilterBefore(clientAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(
                new InternalServiceAuthenticationFilter(internalServiceTokenValidator),
                clientAuthenticationFilter.getClass()
        );
        http.addFilterBefore(
                new PaymentProviderAuthenticationFilter(paymentProviderTokenValidator),
                InternalServiceAuthenticationFilter.class
        );
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
