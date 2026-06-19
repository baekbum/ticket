package dev.bum.admin_service.controller.advice;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<String> handleFeignException(FeignException e) {
        log.error("[Feign 통신 에러] 상태코드: {}, 메시지: {}", e.status(), e.getMessage());

        // 상대방 서비스(예: user-service)가 보내준 원래 에러 JSON Body를 그대로 추출
        String responseBody = e.contentUTF8();

        // 상대방이 보낸 HTTP 상태 코드(예: 404, 400 등)와 에러 본문을 그대로 내 화면단으로 전달
        return ResponseEntity
                .status(e.status())
                .body(responseBody);
    }
}
