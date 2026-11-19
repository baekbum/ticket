package dev.bum.ticket_service.controller.advice;

import dev.bum.ticket_service.controller.reservation.reservation.ReservationRefundController;
import dev.bum.ticket_service.exception.reservation.GatewayRefundRequestException;
import feign.FeignException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ReservationRefundController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRefundExceptionHandler {

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<?> busy(PessimisticLockingFailureException failure) {

        return ResponseEntity.status(409).body(Map.of("code", "RESERVATION_PROCESSING",
                "message", "해당 예매에 대한 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(GatewayRefundRequestException.class)
    public ResponseEntity<?> conflict(GatewayRefundRequestException failure) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", failure.getCode());
        body.put("message", failure.getMessage());

        if (failure.getApplication() != null) body.put("application", failure.getApplication());
        return ResponseEntity.status(failure.getStatus()).body(body);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<?> unavailable(FeignException failure) {

        if (failure.status() == 400 || failure.status() == 409) {
            return ResponseEntity.status(409).body(Map.of("code", "GATEWAY_REFUND_REJECTED",
                    "message", "환불 요청이 거절되었습니다. 신청 내용을 확인하거나 기존 신청을 철회해주세요."));
        }

        return ResponseEntity.status(503).body(Map.of("code", "GATEWAY_REFUND_UNCONFIRMED",
                "message", "환불 결과를 확인하지 못했습니다. 같은 신청으로 다시 시도해주세요."));
    }
}
