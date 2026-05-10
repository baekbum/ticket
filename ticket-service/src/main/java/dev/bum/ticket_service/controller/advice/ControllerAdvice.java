package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.exception.EventDuplicateException;
import dev.bum.ticket_service.exception.EventNotExistException;
import dev.bum.ticket_service.exception.SeatDuplicateException;
import dev.bum.ticket_service.exception.SeatNotExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(EventNotExistException.class)
    public ResponseEntity<String> EventNotExistException(EventNotExistException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(EventDuplicateException.class)
    public ResponseEntity<String> EventDuplicateException(EventDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(SeatDuplicateException.class)
    public ResponseEntity<String> SeatDuplicateException(SeatDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(SeatNotExistException.class)
    public ResponseEntity<String> SeatNotExistException(SeatNotExistException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
