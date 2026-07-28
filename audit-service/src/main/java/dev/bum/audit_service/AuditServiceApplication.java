package dev.bum.audit_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.bum.audit_service", "dev.bum.common"})
public class AuditServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditServiceApplication.class, args);
	}

}
