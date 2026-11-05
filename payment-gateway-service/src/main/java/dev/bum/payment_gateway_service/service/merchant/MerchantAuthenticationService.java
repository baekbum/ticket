package dev.bum.payment_gateway_service.service.merchant;

import dev.bum.payment_gateway_service.jpa.merchant.Merchant;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantJpaRepository;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantAuthenticationService {

    // 요청 토큰을 등록된 설정 그룹으로 변환한다.
    private final MerchantCredentialResolver credentials;
    // 설정 그룹과 숫자 가맹점 코드를 연결한다.
    private final MerchantJpaRepository merchants;

    /**
     * 요청 본문과 무관하게 토큰에 연결된 가맹점을 인증한다. 중단 상태의 조회는 허용한다.
     */
    @Transactional(readOnly = true)
    public AuthenticatedMerchant authenticate(String token) {

        String key = credentials.resolve(token);

        Merchant merchant = merchants.findByCredentialKey(key)
                .orElseThrow(() -> new BadCredentialsException("가맹점 인증 정보가 유효하지 않습니다."));

        return new AuthenticatedMerchant(merchant.getMerchantId(), merchant.getCredentialKey());
    }
}
