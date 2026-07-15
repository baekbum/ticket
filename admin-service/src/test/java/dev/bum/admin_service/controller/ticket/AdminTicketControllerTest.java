package dev.bum.admin_service.controller.ticket;

import dev.bum.admin_service.feign.ticket.TicketServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
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
@WebMvcTest(AdminTicketController.class)
class AdminTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TicketServiceClient ticketServiceClient;

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
        given(ticketServiceClient.selectByReservationId(1L)).willReturn(List.of(ticketResponse()));

        mockMvc.perform(get(baseUrl + "/reservation/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(1L))
                .andExpect(jsonPath("$[0].status").value(TicketStatus.PENDING_PAYMENT.name()));

        then(ticketServiceClient).should().selectByReservationId(1L);
    }

    private TicketResponse ticketResponse() {
        return TicketResponse.builder()
                .ticketId(1L)
                .seatId(1L)
                .zone("VIP")
                .seatRow(1)
                .seatCol(1)
                .grade("VIP")
                .price(150000)
                .status(TicketStatus.PENDING_PAYMENT.name())
                .build();
    }
}
