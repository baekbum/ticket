package dev.bum.ticket_service.exception.seat;

public class SeatAlreadyOccupiedException extends RuntimeException {
    public SeatAlreadyOccupiedException(String message) {
        super(message);
    }

    public SeatAlreadyOccupiedException() {
        super();
    }

    public SeatAlreadyOccupiedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatAlreadyOccupiedException(Throwable cause) {
        super(cause);
    }

    protected SeatAlreadyOccupiedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
