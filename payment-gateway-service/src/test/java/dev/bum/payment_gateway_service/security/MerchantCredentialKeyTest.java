package dev.bum.payment_gateway_service.security;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MerchantCredentialKeyTest {

    @Test
    void generatedKeysHaveTheMerchantPrefixAndIndependent256BitSecrets() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String key = MerchantCredentialKey.generate("ticksy");
            assertThat(key).matches("ticksy_[A-Za-z0-9_-]{43}");
            assertThat(MerchantCredentialKey.hash(key)).matches("[0-9a-f]{64}").doesNotContain("ticksy");
            assertThat(MerchantCredentialKey.matches(key, MerchantCredentialKey.hash(key))).isTrue();
            keys.add(key);
        }
        assertThat(keys).hasSize(100);
    }

    @Test
    void unknownChangedMissingAndOversizedSecretsCannotMatchAnIssuedHash() {
        String key = MerchantCredentialKey.generate("ticksy");
        String hash = MerchantCredentialKey.hash(key);
        for (String invalid : new String[]{null, "", " ", "ticksy_wrong", key + " ", "a".repeat(257)}) {
            assertThat(MerchantCredentialKey.matches(invalid, hash)).isFalse();
        }
        assertThat(MerchantCredentialKey.matches(key, null)).isFalse();
        assertThat(MerchantCredentialKey.matches(key, "ticksy")).isFalse();
    }

    @Test
    void aDisplayPrefixCannotInjectHeadersOrConfigurationLines() {
        for (String prefix : new String[]{null, "", "Ticksy", "ticksy\r\nMerchant:2", "ticksy_bad", "a".repeat(64)}) {
            assertThatThrownBy(() -> MerchantCredentialKey.generate(prefix)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
