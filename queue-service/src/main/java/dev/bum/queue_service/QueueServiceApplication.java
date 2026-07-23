package dev.bum.queue_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {"dev.bum.queue_service", "dev.bum.common"})
public class QueueServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueueServiceApplication.class, args);
	}

}
