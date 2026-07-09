package dev.bum.ticket_service.exception.area;

public class AreaNotExistException extends RuntimeException {
    public AreaNotExistException(String message) {
        super(message);
    }
}
