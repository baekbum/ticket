package dev.bum.ticket_service.exception.seat;

public class SeatOccupationFailedException extends RuntimeException {
    public SeatOccupationFailedException(String message) {
        super(message);
    }

    public SeatOccupationFailedException() {
        super();
    }

    public SeatOccupationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatOccupationFailedException(Throwable cause) {
        super(cause);
    }

    protected SeatOccupationFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
