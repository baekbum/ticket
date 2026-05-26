package dev.bum.ticket_service.exception;

public class TicketNotExistException extends RuntimeException {
    public TicketNotExistException(String message) {
        super(message);
    }

    public TicketNotExistException() {
        super();
    }

    public TicketNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public TicketNotExistException(Throwable cause) {
        super(cause);
    }

    protected TicketNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
