package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.IsReservableRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.controller.reservation.reservation.ReservationController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationService reservationService;

    private final String baseUrl = "/api/v1/reservation";

    @Test
    @DisplayName("인증 없이 예약 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("예약 등록 시 로그인 사용자 ID를 사용")
    void reservation_insert() throws Exception {
        InsertReservationRequest info = insertRequest("other-user");

        mockMvc.perform(post(baseUrl + "/insert")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().insertMyReservation("user01", info);
    }

    @Test
    @DisplayName("내 예약 ID로 조회")
    void reservation_select_by_id() throws Exception {
        ReservationResponse response = reservationResponse(1L, 2);
        given(reservationService.selectMyReservation("user01", 1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1L))
                .andExpect(jsonPath("$.userId").value("user01"));

        then(reservationService).should().selectMyReservation("user01", 1L);
    }

    @Test
    @DisplayName("내 예약 조건 조회")
    void reservation_select_by_cond() throws Exception {
        CustomPageResponse<ReservationResponse> response = CustomPageResponse.of(
                List.of(reservationResponse(1L, 2)),
                10,
                0,
                1,
                1
        );
        given(reservationService.selectMyReservations(any(), any())).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select")
                        .with(authentication(userAuthentication("user01")))
                        .param("eventId", "1")
                        .param("status", ReservationStatus.PENDING_PAYMENT.name())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reservationId").value(1L));

        then(reservationService).should().selectMyReservations("user01", ReservationCondRequest.builder()
                .eventId(1L)
                .status(ReservationStatus.PENDING_PAYMENT)
                .page(0)
                .size(10)
                .build());
    }

    @Test
    @DisplayName("내 예약 취소")
    void reservation_cancel() throws Exception {
        CancelReservationRequest info = cancelRequest("other-user");

        mockMvc.perform(put(baseUrl + "/cancel/id/1")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().cancelMyReservation("user01", 1L, info);
    }

    @Test
    @DisplayName("내 예약 가능 여부 확인")
    void reservation_is_reservable() throws Exception {
        IsReservableRequest info = reservableRequest("other-user");

        mockMvc.perform(post(baseUrl + "/reservable")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().isMyReservable("user01", info);
    }

    private InsertReservationRequest insertRequest(String userId) {
        return InsertReservationRequest.builder()
                .orderId("order-1")
                .userId(userId)
                .eventId(1L)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                        SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build()
                ))
                .build();
    }

    private CancelReservationRequest cancelRequest(String userId) {
        return CancelReservationRequest.builder()
                .userId(userId)
                .selectedTicketIdList(List.of(1L, 2L))
                .eventId(1L)
                .build();
    }

    private IsReservableRequest reservableRequest(String userId) {
        return IsReservableRequest.builder()
                .userId(userId)
                .eventId(1L)
                .selectedSeatCnt(2)
                .build();
    }

    private ReservationResponse reservationResponse(long reservationId, int ticketCount) {
        return ReservationResponse.builder()
                .reservationId(reservationId)
                .orderId("order-1")
                .userId("user01")
                .eventId(1L)
                .eventTitle("IU Concert")
                .ticketCount(ticketCount)
                .status(ReservationStatus.PENDING_PAYMENT.name())
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
