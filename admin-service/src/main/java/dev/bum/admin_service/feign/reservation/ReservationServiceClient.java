package dev.bum.admin_service.feign.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "reservation-service", url = "${services.ticket-service.url}", path = "/api/v1/manage/reservation")
public interface ReservationServiceClient {

    @GetMapping("/select/id/{reservationId}")
    ReservationResponse selectById(@PathVariable("reservationId") Long reservationId);

    @PostMapping("/select")
    CustomPageResponse<ReservationResponse> selectByCond(@RequestBody ReservationCondRequest cond);

    @PutMapping("/cancel/id/{reservationId}")
    void cancel(@PathVariable("reservationId") Long reservationId, @RequestBody CancelReservationRequest info);
}
