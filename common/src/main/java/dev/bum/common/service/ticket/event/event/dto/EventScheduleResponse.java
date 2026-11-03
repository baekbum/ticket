package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventScheduleResponse {

    // 공연 식별자.
    private Long eventId;

    // 공연 시작 일시.
    private String eventDateTime;

    // 예매 가능한 좌석 수.
    private Integer availableSeats;

    // 공연 상태.
    private EventStatus status;

    // 좌석 등급별 가격 목록.
    private List<EventSeatPriceResponse> seatPrices;
}
