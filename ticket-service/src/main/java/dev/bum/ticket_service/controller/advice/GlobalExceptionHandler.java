package dev.bum.ticket_service.controller.advice;

import dev.bum.common.error.ErrorCode;
import dev.bum.common.error.ErrorResponse;
import dev.bum.ticket_service.exception.area.AreaLayoutAlreadyExistsException;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.exception.area.AreaNotExistException;
import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.exception.reservation.ReservationDuplicateException;
import dev.bum.ticket_service.exception.reservation.ReservationNotExistException;
import dev.bum.ticket_service.exception.seat.*;
import dev.bum.ticket_service.exception.ticket.TicketDuplicateException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.exception.ticket.TicketNotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

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
    public ResponseEntity<ErrorResponse> handleNotFoundException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(resolveNotFoundErrorCode(ex), ex.getMessage()));
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
    public ResponseEntity<ErrorResponse> handleDuplicateException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(resolveDuplicateErrorCode(ex), ex.getMessage()));
    }

    @ExceptionHandler(AreaLayoutAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAreaLayoutAlreadyExistsException(AreaLayoutAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.AREA_LAYOUT_ALREADY_EXISTS, ex.getMessage()));
    }

    @ExceptionHandler(SeatLayoutAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSeatLayoutAlreadyExistsException(SeatLayoutAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SEAT_LAYOUT_ALREADY_EXISTS, ex.getMessage()));
    }

    // ==========================================
    // Seat 관련 예외 처리
    // ==========================================

    /**
     * 레디스 캐시 예열이 안 되어 있을 때 발생하는 예외 처리 (404 Not Found)
     */
    @ExceptionHandler(SeatCacheNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSeatCacheNotFoundException(SeatCacheNotFoundException ex) {
        log.error("[좌석 예열 누락 발생] 상세 정보: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        ErrorCode.SEAT_CACHE_NOT_FOUND,
                        "현재 예매를 진행할 수 없는 좌석입니다. 고객센터에 문의해주세요."
                ));
    }

    /**
     * 이미 선택된 좌석(이선좌)일 때 발생하는 예외 처리
     */
    @ExceptionHandler(SeatAlreadyOccupiedException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyOccupiedException(SeatAlreadyOccupiedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SEAT_ALREADY_OCCUPIED, ex.getMessage()));
    }

    /**
     * 좌석 선점 프로세스 진행 중에 발생하는 예외 처리
     */
    @ExceptionHandler(SeatOccupationFailedException.class)
    public ResponseEntity<ErrorResponse> handleSeatOccupationFailedException(SeatOccupationFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.SEAT_OCCUPATION_FAILED, ex.getMessage()));
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
    public ResponseEntity<ErrorResponse> handleTicketLimitExceededException(TicketLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.TICKET_LIMIT_EXCEEDED, ex.getMessage()));
    }

    // ==========================================
    // 잘못된 요청 값 예외 처리
    // ==========================================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage(),
                        (first, second) -> first
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, details));
    }

    @ExceptionHandler(QueueAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleQueueAccessDeniedException(QueueAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(ErrorCode.QUEUE_ACCESS_DENIED, ex.getMessage()));
    }

    private ErrorCode resolveNotFoundErrorCode(RuntimeException ex) {
        if (ex instanceof EventNotExistException) {
            return ErrorCode.EVENT_NOT_FOUND;
        }
        if (ex instanceof AreaNotExistException) {
            return ErrorCode.AREA_NOT_FOUND;
        }
        if (ex instanceof SeatNotExistException) {
            return ErrorCode.SEAT_NOT_FOUND;
        }
        if (ex instanceof ReservationNotExistException) {
            return ErrorCode.RESERVATION_NOT_FOUND;
        }
        if (ex instanceof TicketNotExistException) {
            return ErrorCode.TICKET_NOT_FOUND;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode resolveDuplicateErrorCode(RuntimeException ex) {
        if (ex instanceof EventDuplicateException) {
            return ErrorCode.EVENT_DUPLICATE;
        }
        if (ex instanceof AreaDuplicateException) {
            return ErrorCode.AREA_DUPLICATE;
        }
        if (ex instanceof SeatDuplicateException) {
            return ErrorCode.SEAT_DUPLICATE;
        }
        if (ex instanceof ReservationDuplicateException) {
            return ErrorCode.RESERVATION_DUPLICATE;
        }
        if (ex instanceof TicketDuplicateException) {
            return ErrorCode.TICKET_DUPLICATE;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
