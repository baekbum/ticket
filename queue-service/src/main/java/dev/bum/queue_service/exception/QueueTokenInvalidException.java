package dev.bum.queue_service.exception;

public class QueueTokenInvalidException extends RuntimeException {

    public QueueTokenInvalidException(String message) {
        super(message);
    }
}
