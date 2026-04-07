package dev.bum.auth_service.exception;

public class RedisException extends RuntimeException {
    public RedisException(String message) {
        super(message);
    }

    public RedisException() {
        super();
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisException(Throwable cause) {
        super(cause);
    }

    protected RedisException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
