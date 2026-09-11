package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketCouponServiceClient;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupon")
@RequiredArgsConstructor
public class ClientCouponController {

    private final TicketCouponServiceClient ticketCouponServiceClient;

    @GetMapping("/me")
    public ResponseEntity<?> selectMyCoupons(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        try {
            return ResponseEntity.ok(ticketCouponServiceClient.selectMyCoupons(authorizationHeader));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }

    @PostMapping("/available")
    public ResponseEntity<?> checkAvailable(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CouponAvailabilityRequest request
    ) {
        try {
            return ResponseEntity.ok(ticketCouponServiceClient.checkAvailable(authorizationHeader, request));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }
}
