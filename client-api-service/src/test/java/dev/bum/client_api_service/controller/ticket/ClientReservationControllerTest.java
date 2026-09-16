package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.feign.ticket.TicketReservationServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientReservationControllerTest {

    @Test
    void selectByCond_forwardsAuthorizationAndQueryParameters() throws Exception {
        TicketReservationServiceClient client = mock(TicketReservationServiceClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientReservationController(client)).build();
        when(client.selectByCond(eq("Bearer token"), argThat(condition ->
                condition.getStatus() == ReservationStatus.PAID
                        && condition.getPage() == 1
                        && condition.getSize() == 5
                        && condition.getSort().equals(List.of("reservedAt-desc"))
        ))).thenReturn(CustomPageResponse.of(List.<ReservationResponse>of(), 5, 1, 0, 0));

        mvc.perform(get("/api/v1/reservation/select")
                        .header("Authorization", "Bearer token")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "reservedAt-desc"))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_forwardsAuthorizationReservationIdAndBody() throws Exception {
        TicketReservationServiceClient client = mock(TicketReservationServiceClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientReservationController(client)).build();

        mvc.perform(put("/api/v1/reservation/cancel/id/12")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-1",
                                  "selectedTicketIdList": [31],
                                  "eventId": 7,
                                  "refundAccount": null
                                }
                                """))
                .andExpect(status().isOk());

        verify(client).cancel(eq("Bearer token"), eq(12L), argThat(request ->
                request.getUserId().equals("user-1")
                        && request.getSelectedTicketIdList().equals(List.of(31L))
                        && request.getEventId() == 7L
        ));
    }
}
