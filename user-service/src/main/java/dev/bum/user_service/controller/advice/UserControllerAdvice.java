package dev.bum.user_service.controller.advice;

import dev.bum.user_service.exception.UserDuplicateException;
import dev.bum.user_service.exception.UserNotExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserControllerAdvice {

    @ExceptionHandler(UserDuplicateException.class)
    public ResponseEntity<String> UserDuplicateException(UserDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotExistException.class)
    public ResponseEntity<String> UserNotExistException(UserNotExistException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
