package dev.bum.queue_service.exception;

public class EventPolicyException extends IllegalArgumentException {

    private final boolean unavailable;

    public EventPolicyException(String reason) {

        super(switch (reason) {
            case "SALE_NOT_STARTED" -> "아직 판매가 시작되지 않은 공연입니다.";
            case "SALE_ENDED" -> "판매가 종료된 공연입니다.";
            case "POLICY_UNAVAILABLE" -> "공연 예매 정책을 준비 중입니다. 잠시 후 다시 시도해주세요.";
            default -> "현재 예매할 수 없는 공연 상태입니다.";
        });
        unavailable = "POLICY_UNAVAILABLE".equals(reason);
    }

    public boolean isUnavailable() {

        return unavailable; }
}
