package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.controller.seat.SeatController;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.seat.SeatService;
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

import java.time.LocalDateTime;
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
@WebMvcTest(SeatController.class)
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SeatService seatService;

    @MockitoBean
    private QueueAccessService queueAccessService;

    private final String baseUrl = "/api/v1/seat";

    @Test
    @DisplayName("인증 없이 좌석 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("ID로 좌석 조회")
    void seat_select_by_id() throws Exception {
        given(seatService.selectById(1L)).willReturn(seatResponse(1L, "VIP", 1, 1));

        mockMvc.perform(get(baseUrl + "/select/id/1")
                        .with(authentication(userAuthentication("user01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(1L))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        then(seatService).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 좌석 조회")
    void seat_select_by_cond() throws Exception {
        CustomPageResponse<SeatResponse> response = CustomPageResponse.of(
                List.of(seatResponse(1L, "VIP", 1, 1)),
                10,
                0,
                1,
                1
        );

        given(seatService.selectByCond(any())).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select")
                        .with(authentication(userAuthentication("user01")))
                        .param("eventId", "1")
                        .param("grade", "VIP")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seatId").value(1L));

        then(seatService).should().selectByCond(SeatCondRequest.builder()
                .eventId(1L)
                .grade(SeatGrade.VIP)
                .page(0)
                .size(10)
                .build());
    }

    @Test
    @DisplayName("좌석 점유 시 로그인 사용자 ID로 요청 사용자 ID를 덮어쓴다")
    void occupy_seat() throws Exception {
        SeatOccupyRequest request = SeatOccupyRequest.builder()
                .eventId(1L)
                .userId("other-user")
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()))
                .maxTicketsPerPerson(4)
                .build();
        SeatOccupyRequest expected = SeatOccupyRequest.builder()
                .eventId(1L)
                .userId("user01")
                .seats(request.getSeats())
                .maxTicketsPerPerson(4)
                .build();
        SeatOccupyResponse response = SeatOccupyResponse.builder()
                .orderId("order-id")
                .eventId(1L)
                .userId("user01")
                .seats(request.getSeats())
                .expiresAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();

        given(seatService.occupySeat(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/occupy")
                        .with(authentication(userAuthentication("user01")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-id"))
                .andExpect(jsonPath("$.userId").value("user01"));

        then(seatService).should().occupySeat(expected);
    }

    private SeatResponse seatResponse(Long seatId, String zone, Integer row, Integer col) {
        return SeatResponse.builder()
                .seatId(seatId)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .seatName(zone + " " + row + "-" + col)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .eventId(1L)
                .areaId(1L)
                .areaName("VIP")
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime("2026-09-18 18:00")
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
