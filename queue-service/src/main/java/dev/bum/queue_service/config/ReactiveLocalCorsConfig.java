package dev.bum.queue_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class ReactiveLocalCorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 공통 MVC 설정과 동일한 로컬 브라우저 출처를 허용한다.
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000", "http://localhost:8999", "http://127.0.0.1:8999",
                "http://localhost", "https://localhost", "http://127.0.0.1",
                "https://127.0.0.1", "http://[::1]", "https://[::1]"));
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
