package dev.bum.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    LOGIN_FAILED("아이디 또는 비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND("존재하지 않는 사용자입니다."),
    UNAUTHORIZED("인증이 필요합니다."),
    TOKEN_EXPIRED("토큰이 만료되었습니다."),
    INVALID_TOKEN("유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_REQUIRED("Refresh Token이 필요합니다."),
    REFRESH_TOKEN_INVALID("유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_MISMATCH("Refresh Token 정보가 일치하지 않습니다."),
    REDIS_ERROR("Redis 처리 중 오류가 발생했습니다."),
    VALIDATION_FAILED("요청 값이 올바르지 않습니다."),
    REQUIRED_HEADER_MISSING("필수 헤더가 누락되었습니다."),
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다.");

    private final String message;
}
