package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.controller.event.EventController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.event.event.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private EventService eventService;

    private final String baseUrl = "/api/v1/event";

    @Test
    @DisplayName("인증 없이 이벤트 목록 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("사용자용 이벤트 상세 조회")
    void event_select_by_id() throws Exception {
        EventResponse response = eventResponse(1L, "IU Concert");

        given(eventService.selectVisibleById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.title").value("IU Concert"));

        then(eventService).should().selectVisibleById(1L);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("사용자용 이벤트 목록 조회")
    void event_select_by_cond() throws Exception {
        CustomPageResponse<EventResponse> response = CustomPageResponse.of(
                List.of(eventResponse(1L, "IU Concert")),
                10,
                0,
                1,
                1
        );

        given(eventService.selectVisibleByCond(any())).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select")
                        .param("artistName", "IU")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(1L))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(eventService).should().selectVisibleByCond(EventCondRequest.builder()
                .artistName("IU")
                .page(0)
                .size(10)
                .build());
    }

    private EventResponse eventResponse(Long eventId, String title) {
        return EventResponse.builder()
                .eventId(eventId)
                .artistName("IU")
                .title(title)
                .description("Concert description")
                .venue("KSPO Dome")
                .venueAddress("Seoul")
                .posterUrl("/ticket/uploads/events/posters/" + eventId + "/poster.png")
                .eventDateTime("2026-09-18 18:00")
                .runningMinutes(120)
                .ageLimit(12)
                .totalSeats(14500)
                .availableSeats(14500)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }
}
