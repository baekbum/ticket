package dev.bum.user_service.exception;

public class UserNotExistException extends RuntimeException {
    public UserNotExistException(String message) {
        super(message);
    }

    public UserNotExistException() {
        super();
    }

    public UserNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserNotExistException(Throwable cause) {
        super(cause);
    }

    protected UserNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
