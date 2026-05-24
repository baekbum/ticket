package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerAdvice {

    // ==========================================
    // Event 관련 예외 처리
    // ==========================================
    @ExceptionHandler(EventNotExistException.class)
    public ResponseEntity<String> handleEventNotExistException(EventNotExistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()); // 404
    }

    @ExceptionHandler(EventDuplicateException.class)
    public ResponseEntity<String> handleEventDuplicateException(EventDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }

    // ==========================================
    // Seat 관련 예외 처리
    // ==========================================
    @ExceptionHandler(SeatNotExistException.class)
    public ResponseEntity<String> handleSeatNotExistException(SeatNotExistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()); // 404
    }

    @ExceptionHandler(SeatDuplicateException.class)
    public ResponseEntity<String> handleSeatDuplicateException(SeatDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }

    // ==========================================
    // Reservation 관련 예외 처리
    // ==========================================
    @ExceptionHandler(ReservationNotExistException.class)
    public ResponseEntity<String> handleReservationNotExistException(ReservationNotExistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()); // 404
    }

    @ExceptionHandler(ReservationDuplicateException.class)
    public ResponseEntity<String> handleReservationDuplicateException(ReservationDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }

    // ==========================================
    // Ticket 관련 예외 처리
    // ==========================================
    @ExceptionHandler(TicketNotExistException.class)
    public ResponseEntity<String> handleTicketNotExistException(TicketNotExistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()); // 404
    }

    @ExceptionHandler(TicketDuplicateException.class)
    public ResponseEntity<String> handleTicketDuplicateException(TicketDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }

    @ExceptionHandler(TicketLimitExceededException.class)
    public ResponseEntity<String> handleTicketLimitExceededException(TicketLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }
}