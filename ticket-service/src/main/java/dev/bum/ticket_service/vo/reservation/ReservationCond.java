package dev.bum.ticket_service.vo.reservation;

import dev.bum.ticket_service.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.seat.Seat;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationCond {

    private String userId;
    private Event event;
    private Seat seat;
    private LocalDate startDate;
    private LocalDate endDate;
    private ReservationStatus status;

    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시
}
