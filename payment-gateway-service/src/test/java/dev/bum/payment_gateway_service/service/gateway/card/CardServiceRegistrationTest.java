package dev.bum.payment_gateway_service.service.gateway.card;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import static org.assertj.core.api.Assertions.assertThat;

class CardServiceRegistrationTest {

    @Test
    void currentPgAndFinancialServicesAreRegisteredWithoutLegacyApproval() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(beans);
        scanner.scan("dev.bum.payment_gateway_service.service.gateway", "dev.bum.payment_gateway_service.service.financial");
        assertThat(beans.containsBeanDefinition("pgCardApprovalService")).isTrue();
        assertThat(beans.containsBeanDefinition("gatewayCardApprovalService")).isFalse();
        assertThat(beans.containsBeanDefinition("cardApprovalService")).isTrue();
    }
}
