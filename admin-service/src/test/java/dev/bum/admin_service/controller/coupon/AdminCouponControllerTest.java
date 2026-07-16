package dev.bum.admin_service.controller.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.coupon.CouponServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.coupon.coupon.dto.*;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminCouponController.class)
class AdminCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CouponServiceClient couponServiceClient;

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
        InsertCouponRequest info = insertRequest();
        given(couponServiceClient.insert(any())).willReturn(couponResponse());

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value(1L));

        then(couponServiceClient).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("쿠폰 수정")
    void coupon_update() throws Exception {
        UpdateCouponRequest info = UpdateCouponRequest.builder().discountValue(15000).build();
        given(couponServiceClient.update(1L, info)).willReturn(couponResponse());

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(couponServiceClient).should().update(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 쿠폰 조회")
    void coupon_select_by_id() throws Exception {
        given(couponServiceClient.selectById(1L)).willReturn(couponResponse());

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUMMER10"));

        then(couponServiceClient).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 쿠폰 조회")
    void coupon_select_by_cond() throws Exception {
        CouponCondRequest cond = CouponCondRequest.builder().code("SUMMER").build();
        CustomPageResponse<CouponResponse> response = CustomPageResponse.of(List.of(couponResponse()), 10, 0, 1, 1);
        given(couponServiceClient.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("SUMMER10"));

        then(couponServiceClient).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자에게 쿠폰 발급")
    void coupon_issue() throws Exception {
        IssueCouponRequest info = IssueCouponRequest.builder().userId("user01").couponId(1L).build();
        given(couponServiceClient.issue(any())).willReturn(userCouponResponse());

        mockMvc.perform(post(baseUrl + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCouponId").value(1L));

        then(couponServiceClient).should().issue(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("사용자 ID로 보유 쿠폰 조회")
    void coupon_select_by_user_id() throws Exception {
        given(couponServiceClient.selectByUserId("user01")).willReturn(List.of(userCouponResponse()));

        mockMvc.perform(get(baseUrl + "/user/user01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user01"));

        then(couponServiceClient).should().selectByUserId("user01");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("쿠폰 사용 가능 여부 확인")
    void coupon_check_available() throws Exception {
        CouponAvailabilityRequest info = CouponAvailabilityRequest.builder()
                .userId("user01")
                .userCouponId(1L)
                .orderAmount(50000)
                .build();
        CouponAvailabilityResponse response = CouponAvailabilityResponse.builder().available(true).discountAmount(10000).build();
        given(couponServiceClient.checkAvailable(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/available")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        then(couponServiceClient).should().checkAvailable(info);
    }

    private InsertCouponRequest insertRequest() {
        return InsertCouponRequest.builder()
                .name("Summer Coupon")
                .code("SUMMER10")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .validFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
                .validUntil(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private CouponResponse couponResponse() {
        return CouponResponse.builder()
                .couponId(1L)
                .name("Summer Coupon")
                .code("SUMMER10")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private UserCouponResponse userCouponResponse() {
        return UserCouponResponse.builder()
                .userCouponId(1L)
                .userId("user01")
                .coupon(couponResponse())
                .status(UserCouponStatus.ISSUED)
                .build();
    }
}
