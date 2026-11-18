package dev.bum.payment_gateway_service.security;

import dev.bum.common.config.LocalCorsConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.security.TokenStateStore;
import dev.bum.payment_gateway_service.security.merchant.MerchantAuthenticationFilter;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
    private final TokenStateStore tokenStateStore;
    private final PaymentProviderTokenValidator paymentProviderTokenValidator;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_PAYMENT_PROVIDER = "PAYMENT_PROVIDER";

    @Bean
    @Order(1)
    public SecurityFilterChain financialSimulationFilterChain(
            HttpSecurity http,
            FinancialSimulationToken token
    ) throws Exception {

        return http.securityMatcher("/internal/financial-simulations/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("FINANCIAL_SIMULATOR"))
                .addFilterBefore(new FinancialSimulationAuthenticationFilter(token),
                        UsernamePasswordAuthenticationFilter.class).build();
    }

    @Bean
    public FinancialSimulationToken financialSimulationToken(
            @Value("${app.financial-simulation.token:}") String configuredToken
    ) {

        return new FinancialSimulationToken(configuredToken);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain merchantFilterChain(
            HttpSecurity http,
            MerchantAuthenticationService merchants
    ) throws Exception {

        http.securityMatcher("/api/*/payments/gateway/**", "/api/*/payments/virtual-account/gateway/**",
                        "/api/*/payments/card/gateway/**")
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("MERCHANT"))
                .addFilterBefore(new MerchantAuthenticationFilter(merchants), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(this::configureCors)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/*/payments/card/checkout/*/approve").hasRole(ROLE_USER)
                        .requestMatchers(HttpMethod.GET, "/api/*/payments/card/checkout/*").hasRole(ROLE_USER)
                        .requestMatchers(HttpMethod.POST,
                                "/api/*/banks/virtual-accounts/deposit"
                        ).hasRole(ROLE_PAYMENT_PROVIDER) // 은행 입금 제공자의 업무 진입점
                        .anyRequest().denyAll()
                );

        configureAuthenticationFilters(http);

        return http.build();
    }

    private void configureAuthenticationFilters(HttpSecurity http) {

        JwtAuthenticationFilter clientAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, tokenStateStore);

        // 사용자 JWT와 은행 입금 제공자 인증만 등록한다. 가맹점·금융 인증은 별도 체인이다.
        http.addFilterBefore(clientAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(
                new PaymentProviderAuthenticationFilter(paymentProviderTokenValidator),
                clientAuthenticationFilter.getClass()
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
