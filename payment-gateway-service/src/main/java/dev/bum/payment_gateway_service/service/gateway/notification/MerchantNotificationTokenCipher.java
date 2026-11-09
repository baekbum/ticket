package dev.bum.payment_gateway_service.service.gateway.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MerchantNotificationTokenCipher {

    // 전역 암호화 키는 환경 설정에서만 주입한다. 가맹점별 인증값과 함께 DB에 저장하지 않는다.
    private final SecretKeySpec key;
    // 매 암호화마다 새로운 GCM nonce를 생성한다.
    private final SecureRandom random = new SecureRandom();

    /**
     * 전용 환경 키를 사용하며 기존 로컬 실행은 기존 내부 서비스 비밀값을 재사용한다.
     */
    public MerchantNotificationTokenCipher(
            @Value("${app.merchants.notifications.encryption-key:${app.ticket.service-token}}") String secret
    ) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("가맹점 통보 인증값 암호화 키가 필요합니다.");
        }

        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("가맹점 통보 인증값 암호화를 초기화할 수 없습니다.");
        }
    }

    /**
     * 평문 토큰을 무작위 nonce와 인증 태그가 포함된 버전 1 암호문으로 바꾼다.
     */
    public String encrypt(String token) {

        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new IllegalArgumentException("가맹점 통보 인증값이 올바르지 않습니다.");
        }

        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("가맹점 통보 인증값을 암호화할 수 없습니다.");
        }
    }

    /**
     * 등록 대상의 인증값을 전송 직전에 복호화한다. 손상·키 불일치는 비밀값 없이 거절한다.
     */
    public String decrypt(String ciphertext) {

        try {
            if (ciphertext == null || !ciphertext.startsWith("v1:")) {
                throw new IllegalArgumentException();
            }

            byte[] value = Base64.getDecoder().decode(ciphertext.substring(3));

            if (value.length <= 28) {
                throw new IllegalArgumentException();
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, value, 0, 12));
            String token = new String(cipher.doFinal(value, 12, value.length - 12), StandardCharsets.UTF_8);

            if (token.isBlank()) {
                throw new IllegalArgumentException();
            }

            return token;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("가맹점 통보 인증값을 복호화할 수 없습니다.");
        }
    }
}
