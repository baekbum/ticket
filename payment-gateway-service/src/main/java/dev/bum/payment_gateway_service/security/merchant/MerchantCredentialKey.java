package dev.bum.payment_gateway_service.security.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class MerchantCredentialKey {

    // 접두사와 무관하게 인증 키마다 256비트의 무작위 비밀값을 생성한다.
    private static final SecureRandom RANDOM = new SecureRandom();

    // 상태 없이 사용하는 자격증명 생성·해시 도구다.
    private MerchantCredentialKey() {
    }

    // 접두사는 표시 용도이며 가맹점 소유권은 Merchant 헤더와 DB 해시로 확인한다.
    public static String generate(String merchantPrefix) {

        if (merchantPrefix == null || !merchantPrefix.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("가맹점 키 접두사는 영문 소문자로 시작하는 영문·숫자·하이픈이어야 합니다.");
        }

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);

        return merchantPrefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    // 고엔트로피 발급 키의 원문 대신 SHA-256 해시만 DB에 보관한다.
    public static String hash(String credentialKey) {

        if (credentialKey == null || credentialKey.isBlank() || credentialKey.length() > 256
                || !credentialKey.equals(credentialKey.strip())) {
            throw new IllegalArgumentException("가맹점 인증 키가 유효하지 않습니다.");
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(credentialKey.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("가맹점 인증 해시를 사용할 수 없습니다.", exception);
        }
    }

    // 미발급·형식 오류를 거절하고 해시를 일정 시간 비교한다. 원문이나 해시를 오류에 포함하지 않는다.
    public static boolean matches(String credentialKey, String credentialHash) {

        if (credentialHash == null || !credentialHash.matches("[0-9a-f]{64}")) {
            return false;
        }

        try {
            return MessageDigest.isEqual(hash(credentialKey).getBytes(StandardCharsets.US_ASCII),
                    credentialHash.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
