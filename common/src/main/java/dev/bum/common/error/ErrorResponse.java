package dev.bum.common.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private Object details;

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Object details) {
        return of(errorCode, errorCode.getMessage(), details);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Object details) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .details(details)
                .build();
    }
}
