package dev.bum.admin_service.controller.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.error.ErrorCode;
import dev.bum.common.error.ErrorResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException e) {
        if (e.status() >= 400 && e.status() < 500) {
            log.warn("[Feign 클라이언트 에러] 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
        } else {
            log.error("[Feign 통신 에러] 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
        }

        return ResponseEntity
                .status(resolveFeignStatus(e))
                .body(resolveFeignErrorResponse(e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, ex.getMessage()));
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

    private HttpStatus resolveFeignStatus(FeignException e) {
        HttpStatus status = HttpStatus.resolve(e.status());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }

    private ErrorResponse resolveFeignErrorResponse(FeignException e) {
        String responseBody = e.contentUTF8();
        if (responseBody == null || responseBody.isBlank()) {
            return ErrorResponse.of(ErrorCode.FEIGN_CLIENT_ERROR, "외부 서비스 응답을 받지 못했습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode codeNode = root.get("code");
            JsonNode messageNode = root.get("message");

            if (codeNode != null && messageNode != null) {
                JsonNode detailsNode = root.get("details");
                return ErrorResponse.builder()
                        .code(codeNode.asText())
                        .message(messageNode.asText())
                        .details(detailsNode == null || detailsNode.isNull()
                                ? null
                                : objectMapper.convertValue(detailsNode, Object.class))
                        .build();
            }
        } catch (Exception parseException) {
            log.warn("[Feign 에러 응답 파싱 실패] 원본 응답: {}", responseBody);
        }

        return ErrorResponse.of(ErrorCode.FEIGN_CLIENT_ERROR, responseBody);
    }
}
