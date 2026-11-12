package dev.bum.payment_gateway_service.dto.bank;

public record BankExpirationAcknowledgement(
        // PG가 로컬 커밋까지 마친 은행 만료 이벤트의 불변 스냅샷.
        BankExpirationEvent event
) {

    // 가맹점 전달이 아닌 PG의 수신 커밋만 확인한다.
    public static BankExpirationAcknowledgement of(BankExpirationEvent event) {

        return new BankExpirationAcknowledgement(event);
    }

    // 다른 거래·금액·기한·만료 시각의 성공 응답은 수락하지 않는다.
    public boolean matches(BankExpirationEvent expected) {

        return expected.equals(event);
    }
}
