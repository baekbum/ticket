package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.exception.area.AreaLayoutAlreadyExistsException;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.exception.area.AreaNotExistException;
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
public class GlobalExceptionHandler {

    // ==========================================
    // 데이터를 발견하지 못했을 경우의 예외 처리
    // ==========================================
    @ExceptionHandler({
            EventNotExistException.class,
            AreaNotExistException.class,
            SeatNotExistException.class,
            ReservationNotExistException.class,
            TicketNotExistException.class
    })
    public ResponseEntity<String> handleNotFoundException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    // ==========================================
    // 중복된 데이터가 존재하는 경우의 예외 처리
    // ==========================================
    @ExceptionHandler({
            EventDuplicateException.class,
            AreaDuplicateException.class,
            SeatDuplicateException.class,
            ReservationDuplicateException.class,
            TicketDuplicateException.class
    })
    public ResponseEntity<String> handleDuplicateException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AreaLayoutAlreadyExistsException.class)
    public ResponseEntity<String> handleAreaLayoutAlreadyExistsException(AreaLayoutAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // ==========================================
    // Seat 관련 예외 처리
    // ==========================================

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
    // Ticket 관련 예외 처리
    // ==========================================

    /**
     * 예매할 수 있는 최대치를 초과했을 경우
     * @param ex
     * @return
     */
    @ExceptionHandler(TicketLimitExceededException.class)
    public ResponseEntity<String> handleTicketLimitExceededException(TicketLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400
    }

    // ==========================================
    // 잘못된 요청 값 예외 처리
    // ==========================================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
