package dev.bum.ticket_service.exception;

public class SeatNotExistException extends RuntimeException {
    public SeatNotExistException(String message) {
        super(message);
    }

    public SeatNotExistException() {
        super();
    }

    public SeatNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatNotExistException(Throwable cause) {
        super(cause);
    }

    protected SeatNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
