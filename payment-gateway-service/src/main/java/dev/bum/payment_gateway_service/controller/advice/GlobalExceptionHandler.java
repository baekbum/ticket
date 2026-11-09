package dev.bum.payment_gateway_service.controller.advice;

import dev.bum.payment_gateway_service.exception.TicketPaymentCompleteException;
import dev.bum.payment_gateway_service.exception.CardPaymentPendingException;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.exception.GatewayPaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.exception.FinancialApprovalRejectedException;

import java.util.Map;
import dev.bum.payment_gateway_service.exception.FinancialApiException;
import dev.bum.payment_gateway_service.exception.FinancialPaymentPendingException;
import dev.bum.payment_gateway_service.dto.gateway.GatewayApiErrorResponse;
import dev.bum.payment_gateway_service.dto.gateway.GatewayCardPaymentPendingResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FinancialApiException.class)
    public ResponseEntity<GatewayApiErrorResponse> handleFinancialApiError(FinancialApiException exception) {

        return ResponseEntity.status(exception.getStatusCode()).body(GatewayApiErrorResponse.from(exception.getResponse()));
    }

    @ExceptionHandler(FinancialPaymentPendingException.class)
    public ResponseEntity<GatewayCardPaymentPendingResponse> handleFinancialPending(
            FinancialPaymentPendingException exception
    ) {

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GatewayCardPaymentPendingResponse.from(exception.getResponse()));
    }

    @ExceptionHandler(FinancialApprovalRejectedException.class)
    public ResponseEntity<Map<String, String>> handleCardRejection(FinancialApprovalRejectedException exception) {

        return ResponseEntity.badRequest().body(Map.of("code", "CARD_APPROVAL_REJECTED",
                "message", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleStorageFailure(DataAccessException exception) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "GATEWAY_STORAGE_UNAVAILABLE",
                "message", "PG 처리 결과를 저장하지 못했습니다. 같은 PG 결제 식별자로 재요청해주세요."));
    }

    @ExceptionHandler({ResourceAccessException.class, FinancialProviderResponseException.class})
    public ResponseEntity<Map<String, String>> handleFinancialTransportFailure(RuntimeException exception) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "FINANCIAL_PROVIDER_UNAVAILABLE",
                "message", "금융 처리 결과를 확인할 수 없습니다. 같은 결제번호로 상태를 확인하거나 재요청해주세요."));
    }

    @ExceptionHandler(GatewayPaymentConflictException.class)
    public ResponseEntity<Map<String, String>> handleGatewayConflict(GatewayPaymentConflictException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "GATEWAY_PAYMENT_REQUEST_MISMATCH", "message", exception.getMessage()));
    }

    @ExceptionHandler(GatewayPaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGatewayNotFound(GatewayPaymentNotFoundException exception) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "GATEWAY_PAYMENT_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(CardPaymentPendingException.class)
    public ResponseEntity<Map<String, String>> handlePending(CardPaymentPendingException e) {

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "paymentNo", e.getPaymentNo(), "status", "PENDING", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {

        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException() {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(TicketPaymentCompleteException.class)
    public ResponseEntity<Map<String, String>> handleTicketPaymentCompleteException(TicketPaymentCompleteException e) {

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
    }
}
