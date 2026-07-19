package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetailResponse {

    private ReservationResponse reservation;

    @Builder.Default
    private List<TicketResponse> tickets = new ArrayList<>();

    @Builder.Default
    private List<ReservationDiscountResponse> discounts = new ArrayList<>();

    private ReservationDeliveryResponse delivery;
    private PaymentResponse payment;

    private Integer totalTicketAmount;
    private Integer totalDiscountAmount;
    private Integer paymentAmount;
}
