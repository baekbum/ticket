package dev.bum.ticket_service.exception;

public class TicketDuplicateException extends RuntimeException {
    public TicketDuplicateException(String message) {
        super(message);
    }

    public TicketDuplicateException() {
        super();
    }

    public TicketDuplicateException(String message, Throwable cause) {
        super(message, cause);
    }

    public TicketDuplicateException(Throwable cause) {
        super(cause);
    }

    protected TicketDuplicateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
