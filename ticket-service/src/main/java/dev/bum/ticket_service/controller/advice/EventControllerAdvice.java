package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.exception.EventDuplicateException;
import dev.bum.ticket_service.exception.EventNotExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EventControllerAdvice {

    @ExceptionHandler(EventNotExistException.class)
    public ResponseEntity<String> EventNotExistException(EventNotExistException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(EventDuplicateException.class)
    public ResponseEntity<String> EventDuplicateException(EventDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
