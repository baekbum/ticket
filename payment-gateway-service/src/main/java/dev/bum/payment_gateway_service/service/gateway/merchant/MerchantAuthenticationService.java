package dev.bum.payment_gateway_service.service.gateway.merchant;

import dev.bum.payment_gateway_service.jpa.merchant.Merchant;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantJpaRepository;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantAuthenticationService {

    // 헤더의 숫자 가맹점 코드로 원문 키 대신 해시를 보관한 가맹점을 조회한다.
    private final MerchantJpaRepository merchants;

    /**
     * Merchant·credentialKey 헤더를 DB 해시로 인증한다. 중단 상태의 기존 거래 조회는 허용한다.
     */
    @Transactional(readOnly = true)
    public AuthenticatedMerchant authenticate(String merchantHeader, String credentialKey) {

        if (merchantHeader == null || !merchantHeader.matches("[1-9][0-9]{0,18}")) {
            throw invalidCredential();
        }

        long merchantId;

        try {
            merchantId = Long.parseLong(merchantHeader);
        } catch (NumberFormatException exception) {
            throw invalidCredential();
        }

        Merchant merchant = merchants.findById(merchantId).orElseThrow(this::invalidCredential);

        if (!MerchantCredentialKey.matches(credentialKey, merchant.getCredentialHash())) {
            throw invalidCredential();
        }

        return new AuthenticatedMerchant(merchant.getMerchantId());
    }

    /**
     * 미등록 가맹점과 잘못된 키를 구분하거나 인증값을 노출하지 않는다.
     */
    private BadCredentialsException invalidCredential() {

        return new BadCredentialsException("가맹점 인증 정보가 유효하지 않습니다.");
    }
}
