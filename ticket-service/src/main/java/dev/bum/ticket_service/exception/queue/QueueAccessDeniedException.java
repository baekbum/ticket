package dev.bum.ticket_service.exception.queue;

public class QueueAccessDeniedException extends RuntimeException {

    public QueueAccessDeniedException(String message) {
        super(message);
    }
}
