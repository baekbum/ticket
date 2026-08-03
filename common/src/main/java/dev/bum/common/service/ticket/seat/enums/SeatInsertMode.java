package dev.bum.common.service.ticket.seat.enums;

public enum SeatInsertMode {
    /**
     * 기존 좌석이 존재하면 등록하지 않고 충돌 응답을 반환한다.
     */
    FAIL_IF_EXISTS,

    /**
     * 기존 좌석을 모두 삭제한 뒤 새 좌석 데이터를 등록한다.
     */
    REPLACE,

    /**
     * 기존 좌석은 유지하고 요청으로 전달된 좌석 데이터를 추가 등록한다.
     */
    APPEND
}
