package dev.bum.ticket_service.controller.reservation.reservation;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.ticket_service.service.payment.refund.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservation/{reservationId}/refunds")
public class ReservationRefundController {

    private final GatewayReservationRefundService refunds;

    @PostMapping
    public GatewayRefundActionResponse request(
            @AuthenticationPrincipal String userId,
            @PathVariable long reservationId,
            @RequestBody CancelReservationRequest request
    ) {

        return refunds.request(userId, reservationId, request);
    }

    @GetMapping
    public List<PaymentRefundProcessResponse> find(
            @AuthenticationPrincipal String userId,
            @PathVariable long reservationId
    ) {

        return refunds.find(userId, reservationId);
    }

    @PostMapping("/{processId}/retry")
    public GatewayRefundActionResponse retry(
            @AuthenticationPrincipal String userId,
            @PathVariable long reservationId,
            @PathVariable long processId
    ) {

        return refunds.retry(userId, reservationId, processId);
    }

    @PostMapping("/{processId}/withdraw")
    public GatewayRefundActionResponse withdraw(
            @AuthenticationPrincipal String userId,
            @PathVariable long reservationId,
            @PathVariable long processId
    ) {

        return refunds.withdraw(userId, reservationId, processId);
    }
}
