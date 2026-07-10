package dev.bum.user_service.exception;

public class UserAddressNotExistException extends RuntimeException {
    public UserAddressNotExistException(String message) {
        super(message);
    }
}
