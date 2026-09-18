package dev.bum.support_service.exception;

public class FaqNotFoundException extends RuntimeException {

    public FaqNotFoundException(String message) {
        super(message);
    }
}
