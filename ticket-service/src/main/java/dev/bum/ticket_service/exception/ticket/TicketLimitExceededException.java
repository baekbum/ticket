package dev.bum.ticket_service.exception.ticket;

public class TicketLimitExceededException extends RuntimeException {
    public TicketLimitExceededException(String message) {
        super(message);
    }

    public TicketLimitExceededException() {
        super();
    }

    public TicketLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    public TicketLimitExceededException(Throwable cause) {
        super(cause);
    }

    protected TicketLimitExceededException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
