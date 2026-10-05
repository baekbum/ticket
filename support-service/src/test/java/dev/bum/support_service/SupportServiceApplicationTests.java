package dev.bum.support_service;

import dev.bum.common.config.TokenStateConfig;
import dev.bum.common.security.TokenStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class SupportServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBeansOfType(TokenStateStore.class)).hasSize(1);
		assertThat(applicationContext.getBean(TokenStateStore.class))
				.isInstanceOf(TokenStateConfig.ManagedTokenStateStore.class);
	}

}
