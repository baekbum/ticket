package dev.bum.client_api_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"dev.bum.client_api_service", "dev.bum.common"})
public class ClientApiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClientApiServiceApplication.class, args);
	}
}
