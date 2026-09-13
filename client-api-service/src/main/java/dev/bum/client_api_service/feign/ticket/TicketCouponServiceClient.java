package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponFilter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "client-ticket-coupon-service", url = "${services.ticket-service.url}", path = "/api/v1/coupon")
public interface TicketCouponServiceClient {

    @GetMapping("/me")
    List<UserCouponResponse> selectMyCoupons(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("filter") UserCouponFilter filter
    );

    @PostMapping("/available")
    CouponAvailabilityResponse checkAvailable(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CouponAvailabilityRequest request
    );
}
