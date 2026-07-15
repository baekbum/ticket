package dev.bum.ticket_service.controller;

import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.ticket_service.service.coupon.userCoupon.UserCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping("/api/v1/coupon")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final UserCouponService userCouponService;

    @GetMapping("/downloadable")
    public ResponseEntity<List<CouponResponse>> selectDownloadableCoupons(@AuthenticationPrincipal String currentUserId) {
        return ResponseEntity.ok(userCouponService.selectDownloadableCoupons(currentUserId));
    }

    @PostMapping("/download/{couponId}")
    public ResponseEntity<UserCouponResponse> download(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("couponId") Long couponId
    ) {
        return ResponseEntity.ok(userCouponService.issue(currentUserId, couponId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<UserCouponResponse>> selectMyCoupons(@AuthenticationPrincipal String currentUserId) {
        return ResponseEntity.ok(userCouponService.selectByUserId(currentUserId));
    }

    @PostMapping("/available")
    public ResponseEntity<CouponAvailabilityResponse> checkAvailable(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody CouponAvailabilityRequest request
    ) {
        return ResponseEntity.ok(userCouponService.checkAvailable(currentUserId, request));
    }
}
