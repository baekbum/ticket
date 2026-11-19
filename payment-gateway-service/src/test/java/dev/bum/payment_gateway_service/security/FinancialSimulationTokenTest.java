package dev.bum.payment_gateway_service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FinancialSimulationTokenTest {

    @Test
    void missingConfigurationGeneratesDifferentProcessSecretsWithoutPrintingThem() {
        FinancialSimulationToken first = new FinancialSimulationToken("");
        FinancialSimulationToken second = new FinancialSimulationToken(null);
        assertThat(first.matches(first.value())).isTrue();
        assertThat(first.matches(second.value())).isFalse();
        assertThat(first.toString()).doesNotContain(first.value());
        assertThat(first.matches(null)).isFalse();
        assertThat(first.matches("x".repeat(4097))).isFalse();
    }

    @Test
    void sharedConfigurationCanAuthenticateAnExplicitlyConfiguredSimulator() {
        FinancialSimulationToken token = new FinancialSimulationToken("test-only-simulation-token");
        assertThat(token.matches("test-only-simulation-token")).isTrue();
        assertThat(token.matches("test-service-token")).isFalse();
    }
}
