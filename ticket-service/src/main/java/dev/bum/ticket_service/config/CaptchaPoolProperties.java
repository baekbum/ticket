package dev.bum.ticket_service.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.captcha.pool")
public record CaptchaPoolProperties(
        @DefaultValue("1000") @Positive int capacity,
        @DefaultValue("32") @Positive int batchSize
) {
}
