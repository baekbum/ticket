package dev.bum.ticket_service.exception;

public class EventNotExistException extends RuntimeException {
    public EventNotExistException(String message) {
        super(message);
    }

    public EventNotExistException() {
        super();
    }

    public EventNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventNotExistException(Throwable cause) {
        super(cause);
    }

    protected EventNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
