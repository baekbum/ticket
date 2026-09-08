package dev.bum.ticket_service.exception.queue;

public class ActiveTokenExpiredException extends QueueAccessDeniedException {
    public ActiveTokenExpiredException() {
        super("좌석 선택 시간이 초과되었습니다. 예매를 다시 시작해주세요.");
    }
}
