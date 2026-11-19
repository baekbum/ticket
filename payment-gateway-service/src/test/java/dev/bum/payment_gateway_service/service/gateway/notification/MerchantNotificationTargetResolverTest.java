package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.payment_gateway_service.dto.gateway.MerchantNotificationTarget;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantNotificationTargetResolverTest {

    final MerchantNotificationEndpointJpaRepository endpoints = mock(MerchantNotificationEndpointJpaRepository.class);
    final MerchantNotificationTokenCipher cipher = new MerchantNotificationTokenCipher("test-encryption-key");
    final MerchantNotificationTargetResolver resolver = new MerchantNotificationTargetResolver(endpoints, cipher);

    @Test
    void registeredFullUrlsAndEncryptedCredentialsAreResolvedWithoutAddingAPath() {
        register(1L, "https://merchant.example/custom/payment-hook", "registered-secret");
        MerchantNotificationTarget target = resolver.resolve(1L, CardPaymentNotification.CONTRACT, "merchant");
        assertThat(target.url().toString()).isEqualTo("https://merchant.example/custom/payment-hook");
        assertThat(target.serviceToken()).isEqualTo("registered-secret");
        assertThat(target.toString()).doesNotContain("registered-secret");
    }

    @Test
    void anotherMerchantsTargetAndUnsupportedContractsAreRejected() {
        register(1L, "https://merchant.example/hook", "secret");
        assertThatThrownBy(() -> resolver.resolve(2L, CardPaymentNotification.CONTRACT, "merchant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(1L, "TICKSY", "merchant"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidDestinationFormsAreRejectedWithoutEchoingSecrets() {
        for (String uri : new String[]{"file:///tmp/hook", "/relative", "http://secret@merchant.example/hook",
                "http://merchant.example/hook#fragment", "http://merchant.example/hook?token=secret",
                "http://secret@bad host/hook"}) {
            register(1L, uri, "secret");
            assertThatThrownBy(() -> resolver.resolve(1L, CardPaymentNotification.CONTRACT, "merchant"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        }
    }

    @Test
    void disabledTargetsCannotBeUsed() {
        MerchantNotificationEndpoint endpoint = register(1L, "https://merchant.example/hook", "secret");
        endpoint.changeActive(false);
        assertThatThrownBy(() -> resolver.resolve(1L, CardPaymentNotification.CONTRACT, "merchant"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentMerchantsMayUseTheSameTargetKeyWithSeparateCredentials() {
        register(1L, "https://first.example/hook", "first-secret");
        register(2L, "https://second.example/hook", "second-secret");
        assertThat(resolver.resolve(1L, CardPaymentNotification.CONTRACT, "merchant").serviceToken())
                .isEqualTo("first-secret");
        assertThat(resolver.resolve(2L, CardPaymentNotification.CONTRACT, "merchant").serviceToken())
                .isEqualTo("second-secret");
    }

    MerchantNotificationEndpoint register(Long merchantId, String url, String token) {
        MerchantNotificationEndpoint endpoint = MerchantNotificationEndpoint.register(null, "merchant", url,
                cipher.encrypt(token));
        when(endpoints.findByMerchantMerchantIdAndTargetKey(merchantId, "merchant")).thenReturn(Optional.of(endpoint));
        return endpoint;
    }
}
