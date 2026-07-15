package dev.bum.ticket_service.controller;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.controller.ticket.TicketController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.ticket.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TicketService ticketService;

    private final String baseUrl = "/api/v1/ticket";

    @Test
    @DisplayName("인증 정보 없이 티켓 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/reservation/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("예약 ID로 티켓 목록 조회")
    void ticket_select_by_reservation_id() throws Exception {
        List<TicketResponse> response = List.of(
                ticketResponse(1L, 1L, "VIP", 1, 1),
                ticketResponse(2L, 2L, "VIP", 1, 2)
        );

        given(ticketService.selectByReservationId(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/reservation/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(1L))
                .andExpect(jsonPath("$[0].seatId").value(1L))
                .andExpect(jsonPath("$[0].zone").value("VIP"))
                .andExpect(jsonPath("$[0].status").value(TicketStatus.PENDING_PAYMENT.name()))
                .andExpect(jsonPath("$[1].ticketId").value(2L));

        then(ticketService).should().selectByReservationId(1L);
    }

    private TicketResponse ticketResponse(long ticketId, long seatId, String zone, int row, int col) {
        return TicketResponse.builder()
                .ticketId(ticketId)
                .seatId(seatId)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .seatName(zone + " " + row + "-" + col)
                .grade("VIP")
                .price(150000)
                .status(TicketStatus.PENDING_PAYMENT.name())
                .build();
    }
}
