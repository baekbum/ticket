package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.controller.area.AreaManagementController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.area.AreaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AreaManagementController.class)
class AreaManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AreaService areaService;

    private final String baseUrl = "/api/v1/manage/area";

    @Test
    @DisplayName("인증 없이 관리자용 구역 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("JSON 텍스트로 구역 등록")
    void area_insert_json() throws Exception {
        InsertAreaJsonRequest info = InsertAreaJsonRequest.builder().jsonText("[]").build();
        given(areaService.insertJson(any())).willReturn(List.of(areaResponse(1L, "VIP")));

        mockMvc.perform(post(baseUrl + "/insert/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("VIP"))
                .andExpect(jsonPath("$[0].layoutKey").value("VIP"));

        then(areaService).should().insertJson(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("SVG 파일로 구역 등록")
    void area_insert_svg() throws Exception {
        MockMultipartFile eventIdPart = new MockMultipartFile("eventId", "", "text/plain", "1".getBytes());
        MockMultipartFile svgFile = new MockMultipartFile("svgFile", "layout.svg", "image/svg+xml", "<svg/>".getBytes());
        given(areaService.insertSvg(eq(1L), any(), eq(true))).willReturn(List.of(areaResponse(1L, "VIP")));

        mockMvc.perform(multipart(baseUrl + "/insert/svg")
                        .file(eventIdPart)
                        .file(svgFile)
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("VIP"))
                .andExpect(jsonPath("$[0].layoutKey").value("VIP"));

        then(areaService).should().insertSvg(eq(1L), eq(svgFile), eq(true));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
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
                .andExpect(jsonPath("$.layoutId").value(1L));

        then(areaService).should().selectLayout(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 구역 조회")
    void area_select_by_id() throws Exception {
        given(areaService.selectById(1L)).willReturn(areaResponse(1L, "VIP"));

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L))
                .andExpect(jsonPath("$.layoutKey").value("VIP"));

        then(areaService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 구역 조회")
    void area_select_by_cond() throws Exception {
        AreaCondRequest cond = AreaCondRequest.builder().eventId(1L).build();
        CustomPageResponse<AreaResponse> response = CustomPageResponse.of(List.of(areaResponse(1L, "VIP")), 10, 0, 1, 1);
        given(areaService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areaId").value(1L));

        then(areaService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 수정")
    void area_update() throws Exception {
        UpdateAreaRequest info = UpdateAreaRequest.builder().areaName("VIP-A").build();
        given(areaService.update(1L, info)).willReturn(areaResponse(1L, "VIP-A"));

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaName").value("VIP-A"));

        then(areaService).should().update(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 삭제")
    void area_delete() throws Exception {
        given(areaService.delete(1L)).willReturn(areaResponse(1L, "VIP"));

        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L));

        then(areaService).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 일괄 삭제")
    void area_delete_bulk() throws Exception {
        DeleteAreaBulkRequest info = DeleteAreaBulkRequest.builder().areaIds(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(areaService).should().deleteBulk(info);
    }

    private AreaResponse areaResponse(Long areaId, String areaName) {
        return AreaResponse.builder()
                .areaId(areaId)
                .eventId(1L)
                .areaName(areaName)
                .layoutKey(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }
}
