package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.controller.coupon.CouponController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.coupon.userCoupon.UserCouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserCouponService userCouponService;

    private final String baseUrl = "/api/v1/coupon";

    @Test
    @DisplayName("인증 없이 쿠폰 다운로드를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(post(baseUrl + "/download/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("쿠폰 다운로드")
    void coupon_download() throws Exception {
        given(userCouponService.issue("user01", 1L)).willReturn(userCouponResponse(1L, "user01"));

        mockMvc.perform(post(baseUrl + "/download/1")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCouponId").value(1L))
                .andExpect(jsonPath("$.userId").value("user01"));

        then(userCouponService).should().issue("user01", 1L);
    }

    @Test
    @DisplayName("다운로드 가능한 쿠폰 조회")
    void coupon_select_downloadable() throws Exception {
        given(userCouponService.selectDownloadableCoupons("user01")).willReturn(List.of(couponResponse()));

        mockMvc.perform(get(baseUrl + "/downloadable")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].couponId").value(1L))
                .andExpect(jsonPath("$[0].code").value("SUMMER10"));

        then(userCouponService).should().selectDownloadableCoupons("user01");
    }

    @Test
    @DisplayName("내 쿠폰 조회")
    void coupon_select_me() throws Exception {
        given(userCouponService.selectByUserId("user01")).willReturn(List.of(userCouponResponse(1L, "user01")));

        mockMvc.perform(get(baseUrl + "/me")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userCouponId").value(1L))
                .andExpect(jsonPath("$[0].status").value(UserCouponStatus.ISSUED.name()));

        then(userCouponService).should().selectByUserId("user01");
    }

    @Test
    @DisplayName("내 쿠폰 사용 가능 여부 확인")
    void coupon_check_available() throws Exception {
        CouponAvailabilityRequest request = CouponAvailabilityRequest.builder()
                .userId("other-user")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();
        CouponAvailabilityResponse response = CouponAvailabilityResponse.builder()
                .available(true)
                .discountAmount(10000)
                .build();

        given(userCouponService.checkAvailable(any(String.class), any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/available")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.discountAmount").value(10000));

        then(userCouponService).should().checkAvailable("user01", request);
    }

    private UserCouponResponse userCouponResponse(Long userCouponId, String userId) {
        return UserCouponResponse.builder()
                .userCouponId(userCouponId)
                .userId(userId)
                .coupon(couponResponse())
                .status(UserCouponStatus.ISSUED)
                .issuedAt("2026-01-01 00:00:00")
                .expiresAt("2026-12-31 23:59:00")
                .build();
    }

    private CouponResponse couponResponse() {
        return CouponResponse.builder()
                .couponId(1L)
                .name("Summer Coupon")
                .code("SUMMER10")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .minOrderAmount(10000)
                .validFrom("2026-01-01 00:00:00")
                .validUntil("2026-12-31 23:59:00")
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private UsernamePasswordAuthenticationToken userAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
