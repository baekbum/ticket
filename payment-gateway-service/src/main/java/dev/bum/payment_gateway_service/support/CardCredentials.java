package dev.bum.payment_gateway_service.support;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.jpa.card.DummyCard;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class CardCredentials {

    private CardCredentials() {

    }

    // 기존 승인과 PG 승인에서 같은 카드번호 정규화 규칙을 사용한다.
    public static String normalize(String number) {

        return number == null ? "" : number.replaceAll("[^0-9]", "");
    }

    // 카드 원장의 조회 키를 계산한다. 원문·CVC·비밀번호를 이력에 저장하지 않는다.
    public static String hash(String value) {

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("카드 식별자를 계산할 수 없습니다.", exception);
        }
    }

    // PG 승인 작업의 동일 카드 여부를 확인하는 비밀값 없는 식별 해시다.
    public static String identity(CardCompany company, String number) {

        String normalized = normalize(number);

        if (company == null || normalized.length() != 16) {
            throw new IllegalArgumentException("카드 정보 형식이 올바르지 않습니다.");
        }

        return hash(company.name() + "|" + hash(normalized));
    }

    // 응답과 이력에 허용하는 마스킹 카드번호만 생성한다.
    public static String mask(String number) {

        return number.substring(0, 4) + "-****-****-" + number.substring(12);
    }

    // 두 승인 경로가 동일한 카드 인증 규칙을 사용한다. 소유자와 예매자가 다른 기존 결제도 허용한다.
    public static void validate(DummyCard card, String number, String cvc, String password, PasswordEncoder encoder) {

        if (number.length() != 16 || !card.getCardNumberLast4().equals(number.substring(12))) {
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }

        if (!encoder.matches(cvc, card.getCvcHash())) {
            throw new IllegalArgumentException("카드 CVC가 일치하지 않습니다.");
        }

        if (!encoder.matches(password, card.getCardPasswordHash())) {
            throw new IllegalArgumentException("카드 비밀번호가 일치하지 않습니다.");
        }

        if (card.isExpired(LocalDate.now())) {
            throw new IllegalArgumentException("만료된 카드입니다.");
        }
    }
}
