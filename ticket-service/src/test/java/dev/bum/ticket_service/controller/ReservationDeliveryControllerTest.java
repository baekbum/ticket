package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import dev.bum.ticket_service.controller.reservation.delivery.ReservationDeliveryController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.reservation.delivery.ReservationDeliveryService;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(ReservationDeliveryController.class)
class ReservationDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationDeliveryService reservationDeliveryService;

    private final String baseUrl = "/api/v1/reservation/delivery";

    @Test
    @DisplayName("인증 없이 배송 스냅샷 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("본인 예약 배송 스냅샷 등록")
    void insert() throws Exception {
        ReservationDeliveryRequest info = deliveryRequest();
        ReservationDeliveryResponse response = deliveryResponse();
        given(reservationDeliveryService.insertMyDelivery("user01", 1L, info)).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert/reservation/1")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1L))
                .andExpect(jsonPath("$.recipientName").value("receiver"));

        then(reservationDeliveryService).should().insertMyDelivery("user01", 1L, info);
    }

    @Test
    @DisplayName("배송 ID로 본인 예약 배송 스냅샷 조회")
    void select_by_id() throws Exception {
        given(reservationDeliveryService.selectMyById("user01", 10L)).willReturn(deliveryResponse());

        mockMvc.perform(get(baseUrl + "/select/id/10")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationDeliveryId").value(10L));

        then(reservationDeliveryService).should().selectMyById("user01", 10L);
    }

    @Test
    @DisplayName("예약 ID로 본인 예약 배송 스냅샷 조회")
    void select_by_reservation_id() throws Exception {
        given(reservationDeliveryService.selectMyByReservationId("user01", 1L)).willReturn(deliveryResponse());

        mockMvc.perform(get(baseUrl + "/select/reservation/1")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1L));

        then(reservationDeliveryService).should().selectMyByReservationId("user01", 1L);
    }

    private ReservationDeliveryRequest deliveryRequest() {
        return ReservationDeliveryRequest.builder()
                .recipientName("receiver")
                .recipientPhone("010-1234-5678")
                .zipCode("12345")
                .address("Seoul Olympic-ro")
                .detailAddress("101-1001")
                .deliveryMessage("Leave at door")
                .build();
    }

    private ReservationDeliveryResponse deliveryResponse() {
        return ReservationDeliveryResponse.builder()
                .reservationDeliveryId(10L)
                .reservationId(1L)
                .recipientName("receiver")
                .recipientPhone("010-1234-5678")
                .zipCode("12345")
                .address("Seoul Olympic-ro")
                .detailAddress("101-1001")
                .deliveryMessage("Leave at door")
                .status(ReservationDeliveryStatus.READY)
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
