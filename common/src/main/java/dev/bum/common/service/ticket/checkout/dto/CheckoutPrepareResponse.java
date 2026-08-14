package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutPrepareResponse {

    private Long eventId;
    private String orderId;
    private List<SeatInfo> seats;
    private String idempotencyKey;
    private boolean prepared;
    private LocalDateTime preparedAt;
}
