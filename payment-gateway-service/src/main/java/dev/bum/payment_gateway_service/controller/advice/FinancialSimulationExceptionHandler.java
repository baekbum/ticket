package dev.bum.payment_gateway_service.controller.advice;

import dev.bum.payment_gateway_service.controller.simulation.BankController;
import dev.bum.payment_gateway_service.controller.simulation.CardController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {CardController.class, BankController.class})
public class FinancialSimulationExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable(DataAccessException exception) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "FINANCIAL_STORAGE_UNAVAILABLE", "message", "금융 처리 저장소를 사용할 수 없습니다."));
    }
}
