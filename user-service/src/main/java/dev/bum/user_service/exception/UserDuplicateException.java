package dev.bum.user_service.exception;

public class UserDuplicateException extends RuntimeException {
  public UserDuplicateException(String message) {
    super(message);
  }

  public UserDuplicateException() {
    super();
  }

  public UserDuplicateException(String message, Throwable cause) {
    super(message, cause);
  }

  public UserDuplicateException(Throwable cause) {
    super(cause);
  }

  protected UserDuplicateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
