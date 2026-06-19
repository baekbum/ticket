package dev.bum.admin_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 1. 현재 쓰레드에 요청된 브라우저의 HttpServletRequest 객체를 가져옵니다.
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();

                    // 2. 브라우저가 보냈던 'Authorization' 헤더를 그대로 꺼냅니다.
                    String authHeader = request.getHeader("Authorization");

                    // 3. 토큰이 존재한다면, FeignClient가 다른 service로 보낼 요청 템플릿에도 동일하게 꽂아줍니다.
                    if (authHeader != null && !authHeader.isEmpty()) {
                        template.header("Authorization", authHeader);
                    }
                }
            }
        };
    }
}
