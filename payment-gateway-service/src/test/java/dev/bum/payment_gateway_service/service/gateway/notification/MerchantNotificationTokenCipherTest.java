package dev.bum.payment_gateway_service.service.gateway.notification;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class MerchantNotificationTokenCipherTest {

    @Test
    void encryptedTokensSurviveRestartWithTheSameKeyAndUseDifferentNonces() {
        MerchantNotificationTokenCipher cipher = new MerchantNotificationTokenCipher("test-master-key");
        String first = cipher.encrypt("merchant-secret");
        String second = cipher.encrypt("merchant-secret");
        assertThat(first).startsWith("v1:").doesNotContain("merchant-secret").isNotEqualTo(second);
        assertThat(new MerchantNotificationTokenCipher("test-master-key").decrypt(first)).isEqualTo("merchant-secret");
    }

    @Test
    void tamperedOrWrongKeyCiphertextIsRejectedWithoutExposingTheToken() {
        MerchantNotificationTokenCipher cipher = new MerchantNotificationTokenCipher("test-master-key");
        String encrypted = cipher.encrypt("merchant-secret");
        byte[] modified = Base64.getDecoder().decode(encrypted.substring(3));
        modified[modified.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(modified)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("merchant-secret");
        assertThatThrownBy(() -> new MerchantNotificationTokenCipher("another-key").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("merchant-secret");
    }

    @Test
    void missingSecretsAndUnversionedCiphertextAreRejected() {
        assertThatThrownBy(() -> new MerchantNotificationTokenCipher(" ")).isInstanceOf(IllegalArgumentException.class);
        MerchantNotificationTokenCipher cipher = new MerchantNotificationTokenCipher("test-master-key");
        assertThatThrownBy(() -> cipher.encrypt(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt("plaintext-token")).isInstanceOf(IllegalStateException.class);
    }
}
