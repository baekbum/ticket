package dev.bum.ticket_service.exception.queue;

public class QueueUnavailableException extends RuntimeException {
    public QueueUnavailableException() {
        super("예매 가능 여부를 일시적으로 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
}
