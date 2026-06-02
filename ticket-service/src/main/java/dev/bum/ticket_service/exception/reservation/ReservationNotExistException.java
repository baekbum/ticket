package dev.bum.ticket_service.exception.reservation;

public class ReservationNotExistException extends RuntimeException {
    public ReservationNotExistException(String message) {
        super(message);
    }

    public ReservationNotExistException() {
        super();
    }

    public ReservationNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReservationNotExistException(Throwable cause) {
        super(cause);
    }

    protected ReservationNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
