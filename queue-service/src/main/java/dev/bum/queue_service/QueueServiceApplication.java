package dev.bum.queue_service;

import dev.bum.common.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
@Import(JwtTokenProvider.class) // 불필요한 컴포넌트를 제외하고, 필요한 컴포넌트만 가져오도록 변경
public class QueueServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueueServiceApplication.class, args);
	}

}
