package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatOccupyRequest {

    // 공연 식별자.
    private Long eventId;

    // 사용자 ID.
    private String userId;

    // 좌석 목록.
    private List<SeatInfo> seats;

    // 사용자별 최대 구매 가능 티켓 수.
    private Integer maxTicketsPerPerson;

    // 같은 공연의 회차를 묶는 그룹 코드.
    private String eventGroupCode;

    // 구매 수량 제한 적용 범위.
    private TicketLimitScope ticketLimitScope;
}
