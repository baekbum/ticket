package dev.bum.payment_gateway_service.security.merchant;

import dev.bum.payment_gateway_service.config.MerchantCredentialProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class MerchantCredentialResolver {

    // 시작 시 검증하고 복사한 토큰 그룹. 요청이나 로그에 출력하지 않는다.
    private final List<Credential> credentials = new ArrayList<>();

    // 자격증명 설정을 검증하고 중복된 토큰이 없는 그룹 목록을 준비한다.
    public MerchantCredentialResolver(MerchantCredentialProperties properties) {

        properties.getCredentials().forEach((key, tokens) -> {
            if (key == null || key.isBlank() || key.length() > 64 || !key.equals(key.strip()) || tokens == null) {
                throw new IllegalStateException("가맹점 자격증명 설정이 올바르지 않습니다.");
            }

            for (String token : tokens) {
                if (token == null || token.isBlank()) continue;

                byte[] value = token.getBytes(StandardCharsets.UTF_8);

                for (Credential existing : credentials) {
                    if (MessageDigest.isEqual(existing.token, value)) {
                        throw new IllegalStateException("가맹점 자격증명 토큰을 중복 등록할 수 없습니다.");
                    }
                }

                credentials.add(new Credential(key, value));
            }
        });

        if (credentials.isEmpty()) {
            throw new IllegalStateException("가맹점 자격증명 설정이 필요합니다.");
        }
    }

    // 모든 등록 토큰을 비교해 일치하는 자격증명 그룹 키를 반환한다.
    public String resolve(String requestedToken) {

        if (requestedToken == null || requestedToken.isBlank() || requestedToken.length() > 4096) {
            throw new BadCredentialsException("가맹점 인증 정보가 유효하지 않습니다.");
        }

        byte[] requested = requestedToken.getBytes(StandardCharsets.UTF_8);
        String matchedKey = null;

        for (Credential credential : credentials) {
            if (MessageDigest.isEqual(credential.token, requested)) matchedKey = credential.key;
        }

        if (matchedKey == null) {
            throw new BadCredentialsException("가맹점 인증 정보가 유효하지 않습니다.");
        }

        return matchedKey;
    }

    private static final class Credential {

        // DB에 연결할 공개 설정 키.
        private final String key;

        // 외부 노출을 피하기 위해 record/toString을 사용하지 않는 토큰 바이트.
        private final byte[] token;

        // 설정 키와 외부에 노출하지 않을 토큰 바이트를 함께 보관한다.
        private Credential(String key, byte[] token) {

            this.key = key;
            this.token = token;
        }
    }
}
