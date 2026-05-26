package dev.bum.ticket_service.vo.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatCond {

    private Long seatId;
    private Long eventId;
    private String zone;
    private Integer seatRow;
    private Integer seatCol;
    private SeatGrade grade;
    private SeatStatus status;

    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시
}
