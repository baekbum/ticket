package dev.bum.common.service.ticket.event.event.enums;

public enum TicketLimitScope {
    // 각 공연마다 1인당 예매 가능 수량을 제한한다.
    PER_EVENT,

    // 같은 이벤트 그룹 전체에서 1인당 예매 가능 수량을 제한한다.
    PER_GROUP
}
