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
import dev.bum.ticket_service.controller.reservation.reservation.ReservationManagementController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
@WebMvcTest(ReservationManagementController.class)
class ReservationManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationService reservationService;

    private final String baseUrl = "/api/v1/manage/reservation";

    @Test
    @DisplayName("인증 없이 관리자용 예약 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("관리자용 예약 등록")
    void reservation_insert() throws Exception {
        InsertReservationRequest info = insertRequest("user01");

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 예약 조회")
    void reservation_select_by_id() throws Exception {
        ReservationResponse response = reservationResponse(1L, 2);
        given(reservationService.selectById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1L));

        then(reservationService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 예약 조회")
    void reservation_select_by_cond() throws Exception {
        ReservationCondRequest cond = ReservationCondRequest.builder()
                .userId("user01")
                .eventId(1L)
                .status(ReservationStatus.PENDING_PAYMENT)
                .page(0)
                .size(10)
                .build();
        CustomPageResponse<ReservationResponse> response = CustomPageResponse.of(
                List.of(reservationResponse(1L, 2)),
                10,
                0,
                1,
                1
        );

        given(reservationService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reservationId").value(1L));

        then(reservationService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("예약 취소")
    void reservation_cancel() throws Exception {
        CancelReservationRequest info = cancelRequest("user01");

        mockMvc.perform(put(baseUrl + "/cancel/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().cancel(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("예약 가능 여부 확인")
    void reservation_is_reservable() throws Exception {
        IsReservableRequest info = reservableRequest("user01");

        mockMvc.perform(post(baseUrl + "/reservable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().isReservable(info);
    }

    private InsertReservationRequest insertRequest(String userId) {
        return InsertReservationRequest.builder()
                .orderId("order-1")
                .userId(userId)
                .eventId(1L)
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()))
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
}
