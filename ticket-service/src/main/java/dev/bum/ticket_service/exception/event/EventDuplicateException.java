package dev.bum.ticket_service.exception.event;

public class EventDuplicateException extends RuntimeException {
    public EventDuplicateException(String message) {
        super(message);
    }

    public EventDuplicateException() {
        super();
    }

    public EventDuplicateException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventDuplicateException(Throwable cause) {
        super(cause);
    }

    protected EventDuplicateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
