package dev.bum.ticket_service.exception.seat;

public class SeatCacheNotFoundException extends RuntimeException {
    public SeatCacheNotFoundException(String message) {
        super(message);
    }

    public SeatCacheNotFoundException() {
        super();
    }

    public SeatCacheNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatCacheNotFoundException(Throwable cause) {
        super(cause);
    }

    protected SeatCacheNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
