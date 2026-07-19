package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutPrepareRequest {

    @NotBlank
    private String orderId;

    @NotNull
    private Long eventId;

    @NotNull
    private List<SeatInfo> seats;

    private Long userCouponId;

    @Valid
    @NotNull
    private ReservationDeliveryRequest delivery;

    @NotNull
    private PaymentMethod paymentMethod;

    private String idempotencyKey;

    private String depositorName;
}
