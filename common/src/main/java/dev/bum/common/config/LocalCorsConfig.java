package dev.bum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("local")
public class LocalCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. 허용할 Origin (출처) 설정
        // 로컬 개발 시 3000포트에서 오는 요청을 허용합니다.
        configuration.addAllowedOrigin("http://localhost:3000");

        // 2. 허용할 HTTP Method
        // GET, POST뿐만 아니라 브라우저가 미리 찔러보는 OPTIONS까지 모두 허용합니다.
        configuration.addAllowedMethod("*");

        // 3. 허용할 헤더
        // Authorization, Content-Type 등 브라우저가 보내는 모든 헤더를 수용합니다.
        configuration.addAllowedHeader("*");

        // 4. 자격 증명 허용
        // 쿠키나 JWT를 포함한 요청을 주고받을 수 있게 설정합니다.
        configuration.setAllowCredentials(true);

        // 5. 경로 매핑
        // 위 설정을 서버의 모든 경로(/**)에 적용합니다.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
