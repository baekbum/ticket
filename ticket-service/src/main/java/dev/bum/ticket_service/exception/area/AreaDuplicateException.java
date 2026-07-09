package dev.bum.ticket_service.exception.area;

public class AreaDuplicateException extends RuntimeException {
    public AreaDuplicateException(String message) {
        super(message);
    }
}
