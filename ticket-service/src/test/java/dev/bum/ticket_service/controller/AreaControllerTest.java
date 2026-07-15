package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
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
    @DisplayName("인증 정보 없이 구역 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        AreaCondRequest cond = AreaCondRequest.builder().build();

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 등록")
    void area_insert() throws Exception {
        InsertAreaRequest info = insertRequest("VIP");
        AreaResponse response = areaResponse(1L, "VIP");

        given(areaService.insert(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L))
                .andExpect(jsonPath("$.areaName").value("VIP"));

        then(areaService).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 벌크 등록")
    void area_insert_bulk() throws Exception {
        InsertAreaBulkRequest info = InsertAreaBulkRequest.builder()
                .areas(List.of(insertRequest("VIP"), insertRequest("R")))
                .build();
        List<AreaResponse> response = List.of(areaResponse(1L, "VIP"), areaResponse(2L, "R"));

        given(areaService.insertBulk(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("VIP"))
                .andExpect(jsonPath("$[1].areaName").value("R"));

        then(areaService).should().insertBulk(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("JSON 텍스트로 구역 등록")
    void area_insert_json() throws Exception {
        InsertAreaJsonRequest info = InsertAreaJsonRequest.builder()
                .jsonText("[]")
                .build();
        List<AreaResponse> response = List.of(areaResponse(1L, "VIP"));

        given(areaService.insertJson(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/insert/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("VIP"));

        then(areaService).should().insertJson(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("SVG 파일로 구역 등록")
    void area_insert_svg() throws Exception {
        MockMultipartFile eventIdPart = new MockMultipartFile("eventId", "", "text/plain", "1".getBytes());
        MockMultipartFile svgFile = new MockMultipartFile("svgFile", "layout.svg", "image/svg+xml", "<svg/>".getBytes());
        List<AreaResponse> response = List.of(areaResponse(1L, "VIP"));

        given(areaService.insertSvg(eq(1L), any(), eq(true))).willReturn(response);

        mockMvc.perform(multipart(baseUrl + "/insert/svg")
                        .file(eventIdPart)
                        .file(svgFile)
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("VIP"));

        then(areaService).should().insertSvg(eq(1L), eq(svgFile), eq(true));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 레이아웃 조회 성공")
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

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 레이아웃이 없으면 204 응답")
    void select_layout_no_content() throws Exception {
        given(areaService.selectLayout(1L)).willReturn(null);

        mockMvc.perform(get(baseUrl + "/layout/event/1"))
                .andExpect(status().isNoContent());

        then(areaService).should().selectLayout(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 구역 조회")
    void area_select_by_id() throws Exception {
        AreaResponse response = areaResponse(1L, "VIP");

        given(areaService.selectById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L))
                .andExpect(jsonPath("$.areaName").value("VIP"));

        then(areaService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 구역 조회")
    void area_select_by_cond() throws Exception {
        AreaCondRequest cond = AreaCondRequest.builder()
                .eventId(1L)
                .areaName("VIP")
                .build();
        CustomPageResponse<AreaResponse> response = CustomPageResponse.of(List.of(areaResponse(1L, "VIP")), 10, 0, 1, 1);

        given(areaService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areaName").value("VIP"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(areaService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 수정")
    void area_update() throws Exception {
        UpdateAreaRequest info = UpdateAreaRequest.builder()
                .areaName("VIP-A")
                .price(160000)
                .build();
        AreaResponse response = areaResponse(1L, "VIP-A");

        given(areaService.update(1L, info)).willReturn(response);

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
        AreaResponse response = areaResponse(1L, "VIP");

        given(areaService.delete(1L)).willReturn(response);

        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1L));

        then(areaService).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 벌크 삭제")
    void area_delete_bulk() throws Exception {
        DeleteAreaBulkRequest info = DeleteAreaBulkRequest.builder()
                .areaIds(List.of(1L, 2L))
                .build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(areaService).should().deleteBulk(info);
    }

    private InsertAreaRequest insertRequest(String areaName) {
        return InsertAreaRequest.builder()
                .eventId(1L)
                .areaName(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }

    private AreaResponse areaResponse(Long areaId, String areaName) {
        return AreaResponse.builder()
                .areaId(areaId)
                .eventId(1L)
                .eventTitle("IU Concert")
                .areaName(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }
}
