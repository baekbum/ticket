package dev.bum.payment_gateway_service.security;

import dev.bum.payment_gateway_service.config.MerchantCredentialProperties;
import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MerchantCredentialResolverTest {

    @Test
    void duplicateCredentialsAcrossMerchantsFailClosedAtStartup() {
        MerchantCredentialProperties properties = new MerchantCredentialProperties();
        properties.setCredentials(Map.of("a", List.of("same-test-token"), "b", List.of("same-test-token")));
        assertThatThrownBy(() -> new MerchantCredentialResolver(properties)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("same-test-token");
    }

    @Test
    void missingInvalidAndOversizedTokensCannotSelectAMerchant() {
        MerchantCredentialProperties properties = new MerchantCredentialProperties();
        properties.setCredentials(Map.of("registered", List.of("test-token")));
        MerchantCredentialResolver resolver = new MerchantCredentialResolver(properties);
        for (String token : new String[]{null, "", " ", "wrong", "a".repeat(4097)}) {
            assertThatThrownBy(() -> resolver.resolve(token)).isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void unconfiguredAndBlankOnlyCredentialSetsFailClosed() {
        assertThatThrownBy(() -> new MerchantCredentialResolver(new MerchantCredentialProperties()))
                .isInstanceOf(IllegalStateException.class);
        MerchantCredentialProperties properties = new MerchantCredentialProperties();
        properties.setCredentials(Map.of("registered", List.of(" ")));
        assertThatThrownBy(() -> new MerchantCredentialResolver(properties)).isInstanceOf(IllegalStateException.class);
    }
}
