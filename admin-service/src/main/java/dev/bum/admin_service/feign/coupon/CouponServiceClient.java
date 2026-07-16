package dev.bum.admin_service.feign.coupon;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "coupon-service", url = "${services.ticket-service.url}", path = "/api/v1/manage/coupon")
public interface CouponServiceClient {

    @PostMapping("/insert")
    CouponResponse insert(@Valid @RequestBody InsertCouponRequest request);

    @PutMapping("/update/id/{couponId}")
    CouponResponse update(@PathVariable("couponId") Long couponId, @Valid @RequestBody UpdateCouponRequest request);

    @GetMapping("/select/id/{couponId}")
    CouponResponse selectById(@PathVariable("couponId") Long couponId);

    @PostMapping("/select")
    CustomPageResponse<CouponResponse> selectByCond(@RequestBody CouponCondRequest cond);

    @PostMapping("/issue")
    UserCouponResponse issue(@Valid @RequestBody IssueCouponRequest request);

    @GetMapping("/user/{userId}")
    List<UserCouponResponse> selectByUserId(@PathVariable("userId") String userId);

    @PostMapping("/available")
    CouponAvailabilityResponse checkAvailable(@Valid @RequestBody CouponAvailabilityRequest request);
}
