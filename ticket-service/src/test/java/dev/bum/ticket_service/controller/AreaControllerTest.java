package dev.bum.ticket_service.controller;

import dev.bum.common.security.TokenStateStore;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.controller.area.AreaController;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.security.InternalServiceTokenValidator;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.area.AreaService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.queue.QueueAccessService.ActiveSession;
import dev.bum.ticket_service.service.seat.SeatActivityTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AreaController.class)
class AreaControllerTest {
    @MockitoBean
    private TokenStateStore tokenStateStore;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private InternalServiceTokenValidator internalServiceTokenValidator;

    @MockitoBean
    private AreaService areaService;

    @MockitoBean
    private QueueAccessService queueAccessService;

    @MockitoBean
    private SeatActivityTracker seatActivityTracker;

    @MockitoBean
    private TicketingDetectionLogProducer detectionLogProducer;

    private final String baseUrl = "/api/v1/area";

    @Test
    @DisplayName("인증 없이 구역 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("이벤트 레이아웃 조회")
    void select_layout() throws Exception {
        EventLayoutResponse response = EventLayoutResponse.builder()
                .layoutId(1L)
                .eventId(1L)
                .originalFileName("layout.svg")
                .svgText("<svg/>")
                .build();

        given(areaService.selectLayout(1L)).willReturn(response);
        Instant issuedAt = Instant.now();
        ActiveSession session = new ActiveSession(issuedAt, issuedAt.plusSeconds(60));
        given(queueAccessService.validate(1L, "user01", "active")).willReturn(session);

        mockMvc.perform(get(baseUrl + "/layout/event/1").with(authentication(userAuthentication()))
                        .header("X-Active-Token", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutId").value(1L))
                .andExpect(jsonPath("$.svgText").value("<svg/>"));

        then(areaService).should().selectLayout(1L);
        then(seatActivityTracker).should().recordAreaLayoutQuery(1L, "user01", "active", session);
    }

    @Test
    void reports_missing_token_but_not_an_expired_token() throws Exception {
        given(queueAccessService.validate(1L, "user01", null)).willThrow(new QueueAccessDeniedException("토큰이 필요합니다."));
        given(queueAccessService.validate(1L, "user01", "expired")).willThrow(new ActiveTokenExpiredException());

        mockMvc.perform(get(baseUrl + "/layout/event/1").with(authentication(userAuthentication())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(baseUrl + "/layout/event/1").with(authentication(userAuthentication()))
                        .header("X-Active-Token", "expired"))
                .andExpect(status().isGone());

        then(detectionLogProducer).should().report(eq(1L), eq("user01"), isNull(),
                eq(TicketingDetectionType.AREA_LAYOUT_ACTIVE_TOKEN_MISSING), any(Instant.class), eq(Map.of()));
        then(detectionLogProducer).shouldHaveNoMoreInteractions();
        then(areaService).shouldHaveNoInteractions();
        then(seatActivityTracker).shouldHaveNoInteractions();
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("이벤트 레이아웃이 없으면 204 응답")
    void select_layout_no_content() throws Exception {
        given(areaService.selectLayout(1L)).willReturn(null);

        mockMvc.perform(get(baseUrl + "/layout/event/1"))
                .andExpect(status().isNoContent());

        then(areaService).should().selectLayout(1L);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("ID로 구역 조회")
    void area_select_by_id() throws Exception {
        given(areaService.selectById(1L)).willReturn(areaResponse(1L, "VIP", AreaStatus.INACTIVE));

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L))
                .andExpect(jsonPath("$.areaName").value("VIP"))
                .andExpect(jsonPath("$.layoutKey").value("VIP"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        then(areaService).should().selectById(1L);
    }

    @WithMockUser(username = "user01", roles = {"USER"})
    @Test
    @DisplayName("조건으로 구역 조회")
    void area_select_by_cond() throws Exception {
        CustomPageResponse<AreaResponse> response = CustomPageResponse.of(
                List.of(areaResponse(1L, "VIP", AreaStatus.ACTIVE), areaResponse(2L, "R", AreaStatus.INACTIVE)),
                10,
                0,
                2,
                1
        );

        given(areaService.selectByCond(any())).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select")
                        .param("eventId", "1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areaName").value("VIP"))
                .andExpect(jsonPath("$.content[0].layoutKey").value("VIP"))
                .andExpect(jsonPath("$.content[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        then(areaService).should().selectByCond(AreaCondRequest.builder()
                .eventId(1L)
                .page(0)
                .size(10)
                .build());
    }

    private AreaResponse areaResponse(Long areaId, String areaName, AreaStatus status) {
        return AreaResponse.builder()
                .areaId(areaId)
                .eventId(1L)
                .areaName(areaName)
                .layoutKey(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(status)
                .build();
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        return new UsernamePasswordAuthenticationToken("user01", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
