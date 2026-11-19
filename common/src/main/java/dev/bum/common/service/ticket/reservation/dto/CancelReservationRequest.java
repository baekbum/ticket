package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CancelReservationRequest {

    // 사용자 ID.
    @NotBlank
    private String userId;

    // 선택한 티켓 식별자 목록.
    @NotNull
    private List<Long> selectedTicketIdList;

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 환불 계좌 정보.
    @Valid
    private RefundAccountRequest refundAccount;
}
