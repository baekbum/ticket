package dev.bum.common.service.ticket.event.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertEventScheduleRequest {

    // 공연 시작 일시.
    @NotNull
    private LocalDateTime eventDateTime;

    // 예매 판매 시작 시각.
    @NotNull
    private LocalDateTime saleStartAt;

    // 예매 판매 종료 시각.
    @NotNull
    private LocalDateTime saleEndAt;

    // 대기열에서 한 번에 입장시킬 인원 수.
    @jakarta.validation.constraints.Positive
    private Integer queueAdmissionSize;

    // 예매 취소 가능 기한.
    @NotNull
    private LocalDateTime cancelDeadlineAt;
}
