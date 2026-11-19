package dev.bum.common.service.ticket.event.event.policy;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import java.time.LocalDateTime;

/** 판매 종료 시각까지 허용한다. 스케줄러가 아직 상태를 바꾸지 않았어도 시각으로 판단한다. */
public final class EventBookingPolicy {

    /**
     * 외부에서 인스턴스를 생성하지 못하도록 제한한다.
     */
    private EventBookingPolicy() {}

    /**
     * 판매 기간과 공연 상태를 기준으로 예매 가능 여부를 검증한다.
     */
    public static void validate(
            EventStatus status,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime performance,
            LocalDateTime now,
            boolean existingHold
    ) {

        if (start == null || end == null || performance == null || start.isAfter(end)) {
            throw new IllegalArgumentException("공연의 판매 기간이 올바르게 설정되지 않았습니다.");
        }

        if (now.isBefore(start)) throw new IllegalArgumentException("아직 판매가 시작되지 않은 공연입니다.");

        if (now.isAfter(end) || !now.isBefore(performance)) {
            throw new IllegalArgumentException("판매가 종료된 공연입니다.");
        }

        if (status != EventStatus.ON_SALE && !(existingHold && status == EventStatus.SOLD_OUT)) {
            throw new IllegalArgumentException("현재 예매할 수 없는 공연 상태입니다.");
        }
    }
}
