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
import dev.bum.ticket_service.controller.reservation.ReservationController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.reservation.ReservationService;
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
    @DisplayName("인증 정보 없이 예약 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        ReservationCondRequest cond = ReservationCondRequest.builder().build();

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("예약 등록")
    void reservation_insert() throws Exception {
        InsertReservationRequest info = insertRequest();

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().insert(info);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("ID로 예약 조회")
    void reservation_select_by_id() throws Exception {
        ReservationResponse response = reservationResponse(1L, 2);

        given(reservationService.selectById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1L))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.ticketCount").value(2))
                .andExpect(jsonPath("$.status").value(ReservationStatus.PENDING_PAYMENT.name()));

        then(reservationService).should().selectById(1L);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
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
                .andExpect(jsonPath("$.content[0].reservationId").value(1L))
                .andExpect(jsonPath("$.content[0].userId").value("user01"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(reservationService).should().selectByCond(cond);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("예약 취소")
    void reservation_cancel() throws Exception {
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .selectedTicketIdList(List.of(1L, 2L))
                .eventId(1L)
                .build();

        mockMvc.perform(put(baseUrl + "/cancel/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().cancel(1L, info);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("예약 가능 여부 확인")
    void reservation_is_reservable() throws Exception {
        IsReservableRequest info = IsReservableRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedSeatCnt(2)
                .build();

        mockMvc.perform(post(baseUrl + "/reservable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(reservationService).should().isReservable(info);
    }

    private InsertReservationRequest insertRequest() {
        return InsertReservationRequest.builder()
                .orderId("order-1")
                .userId("user01")
                .eventId(1L)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                        SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build()
                ))
                .build();
    }

    private ReservationResponse reservationResponse(long reservationId, int ticketCount) {
        return ReservationResponse.builder()
                .reservationId(reservationId)
                .orderId("order-1")
                .userId("user01")
                .eventId(1L)
                .eventTitle("IU Concert")
                .reservedDate("2026년 9월 1일")
                .eventDateTime("2026년 9월 18일 18시")
                .venue("KSPO Dome")
                .ticketCount(ticketCount)
                .status(ReservationStatus.PENDING_PAYMENT.name())
                .build();
    }
}
