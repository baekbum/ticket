package dev.bum.payment_gateway_service.security;

import dev.bum.payment_gateway_service.jpa.merchant.Merchant;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantJpaRepository;
import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantAuthenticationServiceTest {

    MerchantJpaRepository merchants = mock(MerchantJpaRepository.class);
    MerchantAuthenticationService service = new MerchantAuthenticationService(merchants);

    @Test
    void theDatabaseOwnerAndHashAuthenticateEvenWhenTwoMerchantsHaveTheSameNameAndKeyPrefix() {
        String firstKey = MerchantCredentialKey.generate("ticksy");
        String secondKey = MerchantCredentialKey.generate("ticksy");
        Merchant first = merchant(1L, firstKey);
        Merchant second = merchant(2L, secondKey);
        when(merchants.findById(1L)).thenReturn(Optional.of(first));
        when(merchants.findById(2L)).thenReturn(Optional.of(second));

        assertThat(service.authenticate("1", firstKey).merchantId()).isEqualTo(1L);
        assertThatThrownBy(() -> service.authenticate("2", firstKey)).isInstanceOf(BadCredentialsException.class)
                .hasMessageNotContaining(firstKey);
        assertThat(service.authenticate("2", secondKey).merchantId()).isEqualTo(2L);
        assertThat(service.authenticate("1", firstKey).toString()).doesNotContain(firstKey, first.getCredentialHash());
    }

    @Test
    void malformedMerchantHeadersAreRejectedBeforeAnyDatabaseLookup() {
        for (String header : new String[]{null, "", "ticksy", "0", "-1", "01", "1,2", " 1", "9223372036854775808"}) {
            assertThatThrownBy(() -> service.authenticate(header, "ticksy_secret"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        verifyNoInteractions(merchants);
    }

    @Test
    void rotatingOrRevokingTheStoredHashTakesEffectOnTheNextRequest() {
        String oldKey = MerchantCredentialKey.generate("ticksy");
        String newKey = MerchantCredentialKey.generate("ticksy");
        Merchant merchant = merchant(1L, oldKey);
        when(merchants.findById(1L)).thenReturn(Optional.of(merchant));
        assertThat(service.authenticate("1", oldKey).merchantId()).isEqualTo(1L);

        merchant.changeCredentialHash(MerchantCredentialKey.hash(newKey));
        assertThatThrownBy(() -> service.authenticate("1", oldKey)).isInstanceOf(BadCredentialsException.class);
        assertThat(service.authenticate("1", newKey).merchantId()).isEqualTo(1L);

        merchant.changeCredentialHash(null);
        assertThatThrownBy(() -> service.authenticate("1", newKey)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void anUnknownMerchantCannotAuthenticateWithAnotherMerchantsKey() {
        when(merchants.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("999", MerchantCredentialKey.generate("ticksy")))
                .isInstanceOf(BadCredentialsException.class);
    }

    Merchant merchant(Long id, String key) {
        Merchant merchant = Merchant.register("같은 가맹점명", MerchantCredentialKey.hash(key), "PAYMENT_NOTIFICATION_V1", "default");
        ReflectionTestUtils.setField(merchant, "merchantId", id);
        return merchant;
    }
}
