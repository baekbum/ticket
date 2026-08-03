package dev.bum.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth-service
    LOGIN_FAILED("아이디 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED("토큰이 만료되었습니다."),
    INVALID_TOKEN("유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_REQUIRED("Refresh Token이 필요합니다."),
    REFRESH_TOKEN_INVALID("유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_MISMATCH("Refresh Token 정보가 일치하지 않습니다."),
    REDIS_ERROR("Redis 처리 중 오류가 발생했습니다."),

    // User-service
    USER_NOT_FOUND("존재하지 않는 사용자입니다."),
    USER_DUPLICATE("이미 존재하는 사용자입니다."),
    USER_ADDRESS_NOT_FOUND("존재하지 않는 배송지입니다."),

    // Admin-service
    FEIGN_CLIENT_ERROR("외부 서비스 통신 중 오류가 발생했습니다."),

    // Queue-service
    QUEUE_TOKEN_INVALID("유효하지 않은 대기열 토큰입니다."),

    // Ticket-service
    // Event
    EVENT_NOT_FOUND("존재하지 않는 이벤트입니다."),
    EVENT_DUPLICATE("이미 존재하는 이벤트입니다."),

    // Area
    AREA_NOT_FOUND("존재하지 않는 구역입니다."),
    AREA_DUPLICATE("이미 존재하는 구역입니다."),
    AREA_LAYOUT_ALREADY_EXISTS("이미 등록된 구역 배치도입니다."),

    // Seat
    SEAT_NOT_FOUND("존재하지 않는 좌석입니다."),
    SEAT_DUPLICATE("이미 존재하는 좌석입니다."),
    SEAT_LAYOUT_ALREADY_EXISTS("이미 등록된 좌석 배치입니다."),
    SEAT_CACHE_NOT_FOUND("좌석 캐시 정보가 존재하지 않습니다."),
    SEAT_ALREADY_OCCUPIED("이미 선택되었거나 예매 완료된 좌석입니다."),
    SEAT_OCCUPATION_FAILED("좌석 선점 처리 중 오류가 발생했습니다."),

    // Reservation
    RESERVATION_NOT_FOUND("존재하지 않는 예매입니다."),
    RESERVATION_DUPLICATE("이미 존재하는 예매입니다."),

    // Ticket
    TICKET_NOT_FOUND("존재하지 않는 티켓입니다."),
    TICKET_DUPLICATE("이미 존재하는 티켓입니다."),
    TICKET_LIMIT_EXCEEDED("예매 가능 수량을 초과했습니다."),

    // Queue
    QUEUE_ACCESS_DENIED("대기열 접근 권한이 없습니다."),

    // 공통으로 사용
    UNAUTHORIZED("인증이 필요합니다."),
    FORBIDDEN("접근 권한이 없습니다."),
    INVALID_REQUEST("잘못된 요청입니다."),
    VALIDATION_FAILED("요청 값이 올바르지 않습니다."),
    REQUIRED_HEADER_MISSING("필수 헤더가 누락되었습니다."),

    // 기타
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다.");

    private final String message;
}
