package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponAvailabilityResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponCondRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.IssueCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.dto.UserCouponResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.coupon.coupon.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private CouponService couponService;

    private final String baseUrl = "/api/v1/coupon";

    @Test
    @DisplayName("인증 정보 없이 쿠폰 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("쿠폰 등록")
    void coupon_insert() throws Exception {
        InsertCouponRequest request = insertRequest();
        CouponResponse response = couponResponse(1L, "SUMMER10", 10000);

        given(couponService.insert(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value(1L))
                .andExpect(jsonPath("$.code").value("SUMMER10"));

        then(couponService).should().insert(request);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("쿠폰 수정")
    void coupon_update() throws Exception {
        UpdateCouponRequest request = UpdateCouponRequest.builder()
                .name("Updated Coupon")
                .discountValue(15000)
                .build();
        CouponResponse response = couponResponse(1L, "SUMMER10", 15000);

        given(couponService.update(1L, request)).willReturn(response);

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountValue").value(15000));

        then(couponService).should().update(1L, request);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 쿠폰 조회")
    void coupon_select_by_id() throws Exception {
        CouponResponse response = couponResponse(1L, "SUMMER10", 10000);

        given(couponService.selectById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value(1L))
                .andExpect(jsonPath("$.name").value("Summer Coupon"));

        then(couponService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 쿠폰 조회")
    void coupon_select_by_cond() throws Exception {
        CouponCondRequest cond = CouponCondRequest.builder()
                .code("SUMMER")
                .status(CouponStatus.ACTIVE)
                .build();
        CustomPageResponse<CouponResponse> response = CustomPageResponse.of(
                List.of(couponResponse(1L, "SUMMER10", 10000)),
                10,
                0,
                1,
                1
        );

        given(couponService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].couponId").value(1L))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(couponService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자에게 쿠폰 발급")
    void coupon_issue() throws Exception {
        IssueCouponRequest request = IssueCouponRequest.builder()
                .userId("user01")
                .couponId(1L)
                .expiresAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                .build();
        UserCouponResponse response = userCouponResponse(1L, "user01");

        given(couponService.issue(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCouponId").value(1L))
                .andExpect(jsonPath("$.userId").value("user01"));

        then(couponService).should().issue(request);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 ID로 보유 쿠폰 조회")
    void coupon_select_by_user_id() throws Exception {
        List<UserCouponResponse> response = List.of(userCouponResponse(1L, "user01"));

        given(couponService.selectByUserId("user01")).willReturn(response);

        mockMvc.perform(get(baseUrl + "/user/user01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userCouponId").value(1L))
                .andExpect(jsonPath("$[0].status").value(UserCouponStatus.ISSUED.name()));

        then(couponService).should().selectByUserId("user01");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("쿠폰 사용 가능 여부 확인")
    void coupon_check_available() throws Exception {
        CouponAvailabilityRequest request = CouponAvailabilityRequest.builder()
                .userId("user01")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();
        CouponAvailabilityResponse response = CouponAvailabilityResponse.builder()
                .available(true)
                .discountAmount(10000)
                .build();

        given(couponService.checkAvailable(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/available")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.discountAmount").value(10000));

        then(couponService).should().checkAvailable(request);
    }

    private InsertCouponRequest insertRequest() {
        return InsertCouponRequest.builder()
                .name("Summer Coupon")
                .code("SUMMER10")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .minOrderAmount(10000)
                .validFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
                .validUntil(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private CouponResponse couponResponse(Long couponId, String code, Integer discountValue) {
        return CouponResponse.builder()
                .couponId(couponId)
                .name("Summer Coupon")
                .code(code)
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(discountValue)
                .minOrderAmount(10000)
                .validFrom("2026-01-01 00:00:00")
                .validUntil("2026-12-31 23:59:00")
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private UserCouponResponse userCouponResponse(Long userCouponId, String userId) {
        return UserCouponResponse.builder()
                .userCouponId(userCouponId)
                .userId(userId)
                .coupon(couponResponse(1L, "SUMMER10", 10000))
                .status(UserCouponStatus.ISSUED)
                .issuedAt("2026-01-01 00:00:00")
                .expiresAt("2026-12-31 23:59:00")
                .build();
    }
}
