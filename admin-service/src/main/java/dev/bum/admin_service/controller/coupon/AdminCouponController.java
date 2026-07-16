package dev.bum.admin_service.controller.coupon;

import dev.bum.admin_service.feign.coupon.CouponServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateUserCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponServiceClient couponServiceClient;

    @PostMapping("/insert")
    public ResponseEntity<CouponResponse> insert(@Valid @RequestBody InsertCouponRequest request) {
        return ResponseEntity.ok(couponServiceClient.insert(request));
    }

    @PutMapping("/update/id/{couponId}")
    public ResponseEntity<CouponResponse> update(@PathVariable("couponId") Long couponId, @Valid @RequestBody UpdateCouponRequest request) {
        return ResponseEntity.ok(couponServiceClient.update(couponId, request));
    }

    @GetMapping("/select/id/{couponId}")
    public ResponseEntity<CouponResponse> selectById(@PathVariable("couponId") Long couponId) {
        return ResponseEntity.ok(couponServiceClient.selectById(couponId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<CouponResponse>> selectByCond(@RequestBody CouponCondRequest cond) {
        return ResponseEntity.ok(couponServiceClient.selectByCond(cond));
    }

    @PostMapping("/issue")
    public ResponseEntity<UserCouponResponse> issue(@Valid @RequestBody IssueCouponRequest request) {
        return ResponseEntity.ok(couponServiceClient.issue(request));
    }

    @PutMapping("/user-coupon/update/id/{userCouponId}")
    public ResponseEntity<UserCouponResponse> updateUserCoupon(
            @PathVariable("userCouponId") Long userCouponId,
            @Valid @RequestBody UpdateUserCouponRequest request
    ) {
        return ResponseEntity.ok(couponServiceClient.updateUserCoupon(userCouponId, request));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserCouponResponse>> selectByUserId(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(couponServiceClient.selectByUserId(userId));
    }

    @PostMapping("/user-coupon/select")
    public ResponseEntity<CustomPageResponse<UserCouponResponse>> selectUserCouponsByCond(@RequestBody UserCouponCondRequest cond) {
        return ResponseEntity.ok(couponServiceClient.selectUserCouponsByCond(cond));
    }

    @PostMapping("/available")
    public ResponseEntity<CouponAvailabilityResponse> checkAvailable(@Valid @RequestBody CouponAvailabilityRequest request) {
        return ResponseEntity.ok(couponServiceClient.checkAvailable(request));
    }
}
