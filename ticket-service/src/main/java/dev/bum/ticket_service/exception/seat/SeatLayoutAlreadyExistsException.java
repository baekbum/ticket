package dev.bum.ticket_service.exception.seat;

public class SeatLayoutAlreadyExistsException extends RuntimeException {
    public SeatLayoutAlreadyExistsException(String message) {
        super(message);
    }
}
