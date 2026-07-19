package dev.bum.admin_service.feign.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationDeliveryTrackingRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "reservation-delivery-service", url = "${services.ticket-service.url}", path = "/api/v1/manage/reservation/delivery")
public interface ReservationDeliveryServiceClient {

    @PostMapping("/insert/reservation/{reservationId}")
    ReservationDeliveryResponse insert(@PathVariable("reservationId") Long reservationId, @RequestBody ReservationDeliveryRequest request);

    @GetMapping("/select/id/{reservationDeliveryId}")
    ReservationDeliveryResponse selectById(@PathVariable("reservationDeliveryId") Long reservationDeliveryId);

    @PostMapping("/select")
    CustomPageResponse<ReservationDeliveryResponse> selectByCond(@RequestBody ReservationDeliveryCondRequest cond);

    @GetMapping("/select/reservation/{reservationId}")
    ReservationDeliveryResponse selectByReservationId(@PathVariable("reservationId") Long reservationId);

    @PutMapping("/prepare/id/{reservationDeliveryId}")
    ReservationDeliveryResponse prepare(@PathVariable("reservationDeliveryId") Long reservationDeliveryId);

    @PutMapping("/tracking/id/{reservationDeliveryId}")
    ReservationDeliveryResponse updateTracking(
            @PathVariable("reservationDeliveryId") Long reservationDeliveryId,
            @RequestBody UpdateReservationDeliveryTrackingRequest request
    );

    @PutMapping("/ship/id/{reservationDeliveryId}")
    ReservationDeliveryResponse ship(
            @PathVariable("reservationDeliveryId") Long reservationDeliveryId,
            @RequestBody UpdateReservationDeliveryTrackingRequest request
    );

    @PutMapping("/deliver/id/{reservationDeliveryId}")
    ReservationDeliveryResponse deliver(@PathVariable("reservationDeliveryId") Long reservationDeliveryId);

    @PutMapping("/return/id/{reservationDeliveryId}")
    ReservationDeliveryResponse returnDelivery(@PathVariable("reservationDeliveryId") Long reservationDeliveryId);

    @PutMapping("/cancel/id/{reservationDeliveryId}")
    ReservationDeliveryResponse cancel(@PathVariable("reservationDeliveryId") Long reservationDeliveryId);
}
