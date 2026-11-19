package dev.bum.common.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {

    // 처리 결과 코드.
    private String code;

    // 처리 결과 메시지.
    private String message;

    // 상세 정보 목록.
    private Object details;

    // 전달받은 값으로 처리 정보 객체를 구성한다.
    public static ErrorResponse of(ErrorCode errorCode) {

        return of(errorCode, errorCode.getMessage(), null);
    }

    // 전달받은 값으로 처리 정보 객체를 구성한다.
    public static ErrorResponse of(ErrorCode errorCode, String message) {

        return of(errorCode, message, null);
    }

    // 전달받은 값으로 처리 정보 객체를 구성한다.
    public static ErrorResponse of(ErrorCode errorCode, Object details) {

        return of(errorCode, errorCode.getMessage(), details);
    }

    // 전달받은 값으로 처리 정보 객체를 구성한다.
    public static ErrorResponse of(ErrorCode errorCode, String message, Object details) {

        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .details(details)
                .build();
    }
}
