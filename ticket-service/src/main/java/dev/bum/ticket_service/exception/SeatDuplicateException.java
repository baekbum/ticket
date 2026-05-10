package dev.bum.ticket_service.exception;

public class SeatDuplicateException extends RuntimeException {
    public SeatDuplicateException(String message) {
        super(message);
    }

    public SeatDuplicateException() {
        super();
    }

    public SeatDuplicateException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatDuplicateException(Throwable cause) {
        super(cause);
    }

    protected SeatDuplicateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
