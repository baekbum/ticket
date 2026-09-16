package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "client-ticket-reservation-service", url = "${services.ticket-service.url}", path = "/api/v1/reservation")
public interface TicketReservationServiceClient {

    @GetMapping("/select/detail/{reservationId}")
    ReservationDetailResponse selectDetail(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId
    );

    @GetMapping("/select")
    CustomPageResponse<ReservationResponse> selectByCond(
            @RequestHeader("Authorization") String authorizationHeader,
            @SpringQueryMap ReservationCondRequest condition
    );

    @PutMapping("/cancel/id/{reservationId}")
    void cancel(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId,
            @RequestBody CancelReservationRequest request
    );
}
