package dev.bum.auth_service.exception;

import dev.bum.common.error.ErrorCode;
import lombok.Getter;

@Getter
public class RedisException extends RuntimeException {
    private final ErrorCode errorCode;

    public RedisException(String message) {
        super(message);
        this.errorCode = ErrorCode.REDIS_ERROR;
    }

    public RedisException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public RedisException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RedisException() {
        super();
        this.errorCode = ErrorCode.REDIS_ERROR;
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.REDIS_ERROR;
    }

    public RedisException(Throwable cause) {
        super(cause);
        this.errorCode = ErrorCode.REDIS_ERROR;
    }

    protected RedisException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = ErrorCode.REDIS_ERROR;
    }
}
