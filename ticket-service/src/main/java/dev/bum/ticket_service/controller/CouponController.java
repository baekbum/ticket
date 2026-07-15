package dev.bum.ticket_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.ticket_service.service.coupon.coupon.CouponService;
import dev.bum.ticket_service.service.coupon.userCoupon.UserCouponService;
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
@RequestMapping("/api/v1/coupon")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final UserCouponService userCouponService;

    @PostMapping("/insert")
    public ResponseEntity<CouponResponse> insert(@Valid @RequestBody InsertCouponRequest request) {
        return ResponseEntity.ok(couponService.insert(request));
    }

    @PutMapping("/update/id/{couponId}")
    public ResponseEntity<CouponResponse> update(@PathVariable("couponId") Long couponId, @Valid @RequestBody UpdateCouponRequest request) {
        return ResponseEntity.ok(couponService.update(couponId, request));
    }

    @GetMapping("/select/id/{couponId}")
    public ResponseEntity<CouponResponse> selectById(@PathVariable("couponId") Long couponId) {
        return ResponseEntity.ok(couponService.selectById(couponId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<CouponResponse>> selectByCond(@RequestBody CouponCondRequest cond) {
        return ResponseEntity.ok(couponService.selectByCond(cond));
    }

    @PostMapping("/issue")
    public ResponseEntity<UserCouponResponse> issue(@Valid @RequestBody IssueCouponRequest request) {
        return ResponseEntity.ok(userCouponService.issue(request));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserCouponResponse>> selectByUserId(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userCouponService.selectByUserId(userId));
    }

    @PostMapping("/available")
    public ResponseEntity<CouponAvailabilityResponse> checkAvailable(@Valid @RequestBody CouponAvailabilityRequest request) {
        return ResponseEntity.ok(userCouponService.checkAvailable(request));
    }
}
