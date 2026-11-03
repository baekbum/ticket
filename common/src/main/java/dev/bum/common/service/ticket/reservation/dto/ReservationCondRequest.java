package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservationCondRequest {

    // 사용자 ID.
    private String userId;

    // 공연 식별자.
    private long eventId;

    // 좌석 식별자.
    private long seatId;

    // 조회 시작일.
    private LocalDate startDate;

    // 조회 종료일.
    private LocalDate endDate;

    // 예매 상태.
    private ReservationStatus status;

    // 조회할 페이지.
    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    // 조회 결과 정렬 조건.
    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시
}
