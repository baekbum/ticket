package dev.bum.ticket_service.exception.reservation;

public class ReservationDuplicateException extends RuntimeException {
    public ReservationDuplicateException(String message) {
        super(message);
    }

    public ReservationDuplicateException() {
        super();
    }

    public ReservationDuplicateException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReservationDuplicateException(Throwable cause) {
        super(cause);
    }

    protected ReservationDuplicateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
