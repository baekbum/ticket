package dev.bum.common.service.ticket.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationResponse {

    private long reservationId;
    private String orderId;
    private String userId;
    private long eventId;
    private String eventTitle;
    private String reservedDate;
    private String eventDateTime;
    private String venue;
    private int ticketCount;
    private String status;
}
