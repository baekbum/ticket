package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.controller.area.AreaController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.area.AreaService;
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
@WebMvcTest(AreaController.class)
class AreaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AreaService areaService;

    private final String baseUrl = "/api/v1/area";

    @Test
    @DisplayName("인증 없이 구역 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user01", roles = {"USER"})
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

        mockMvc.perform(get(baseUrl + "/layout/event/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layoutId").value(1L))
                .andExpect(jsonPath("$.svgText").value("<svg/>"));

        then(areaService).should().selectLayout(1L);
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
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(status)
                .build();
    }
}
