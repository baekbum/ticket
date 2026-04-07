package dev.bum.auth_service.controller.advice;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthControllerAdvice {

    @ExceptionHandler(PasswordIncorrectException.class)
    public ResponseEntity<String> PasswordIncorrectException(PasswordIncorrectException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotExistException.class)
    public ResponseEntity<String> UserNotExistException(UserNotExistException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(RedisException.class)
    public ResponseEntity<String> RedisException(RedisException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
