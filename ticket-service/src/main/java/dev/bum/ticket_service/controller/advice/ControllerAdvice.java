package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.exception.reservation.ReservationDuplicateException;
import dev.bum.ticket_service.exception.reservation.ReservationNotExistException;
import dev.bum.ticket_service.exception.seat.*;
import dev.bum.ticket_service.exception.ticket.TicketDuplicateException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.exception.ticket.TicketNotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
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

    /**
     * 레디스 캐시 예열이 안 되어 있을 때 발생하는 예외 처리 (404 Not Found)
     */
    @ExceptionHandler(SeatCacheNotFoundException.class)
    public ResponseEntity<String> handleSeatCacheNotFoundException(SeatCacheNotFoundException ex) {
        log.error("[좌석 예열 누락 발생] 상세 정보: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("현재 예매를 진행할 수 없는 좌석입니다. 고객센터에 문의해주세요.");
    }

    /**
     * 이미 선택된 좌석(이선좌)일 때 발생하는 예외 처리
     */
    @ExceptionHandler(SeatAlreadyOccupiedException.class)
    public ResponseEntity<String> handleSeatAlreadyOccupiedException(SeatAlreadyOccupiedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /**
     * 좌석 선점 프로세스 진행 중에 발생하는 예외 처리
     */
    @ExceptionHandler(SeatOccupationFailedException.class)
    public ResponseEntity<String> handleSeatOccupationFailedException(SeatOccupationFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
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