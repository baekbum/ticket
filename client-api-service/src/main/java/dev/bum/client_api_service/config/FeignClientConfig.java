package dev.bum.client_api_service.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            addHeader(template, "Authorization", request.getHeader("Authorization"));
            addHeader(template, "Authorization-Refresh", request.getHeader("Authorization-Refresh"));
        };
    }

    private void addHeader(feign.RequestTemplate template, String name, String value) {
        if (StringUtils.hasText(value)) {
            template.header(name, value);
        }
    }
}
