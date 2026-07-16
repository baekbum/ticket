package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.controller.event.EventManagementController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.event.event.EventService;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDateTime;
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
@WebMvcTest(EventManagementController.class)
class EventManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private EventService eventService;

    private final String baseUrl = "/api/v1/manage/event";

    @Test
    @DisplayName("인증 없이 관리자용 이벤트 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 등록")
    void event_insert() throws Exception {
        InsertEventRequest info = insertRequest();
        EventResponse response = eventResponse(1L, "IU Concert");
        MockMultipartFile eventPart = jsonPart("event", info);
        MockMultipartFile posterPart = new MockMultipartFile(
                "posterImage",
                "poster.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes()
        );

        given(eventService.insert(any(), any())).willReturn(response);

        mockMvc.perform(multipart(baseUrl + "/insert")
                        .file(eventPart)
                        .file(posterPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.title").value("IU Concert"))
                .andExpect(jsonPath("$.status").value("ON_SALE"));

        then(eventService).should().insert(eq(info), eq(posterPart));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 이벤트 조회")
    void event_select_by_id() throws Exception {
        EventResponse response = eventResponse(1L, "IU Concert");

        given(eventService.selectById(1L)).willReturn(response);

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.title").value("IU Concert"));

        then(eventService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 이벤트 조회")
    void event_select_by_cond() throws Exception {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("IU")
                .build();
        CustomPageResponse<EventResponse> response = CustomPageResponse.of(
                List.of(eventResponse(1L, "IU Concert")),
                10,
                0,
                1,
                1
        );

        given(eventService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(1L))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        then(eventService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("JSON 요청으로 이벤트 수정")
    void event_update_json() throws Exception {
        UpdateEventRequest info = updateRequest();
        EventResponse response = eventResponse(1L, "Updated Concert");

        given(eventService.update(1L, info)).willReturn(response);

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.title").value("Updated Concert"));

        then(eventService).should().update(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("multipart 요청으로 이벤트 포스터 수정")
    void event_update_multipart() throws Exception {
        UpdateEventRequest info = updateRequest();
        EventResponse response = eventResponse(1L, "Updated Concert");
        MockMultipartFile eventPart = jsonPart("event", info);
        MockMultipartFile posterPart = new MockMultipartFile(
                "posterImage",
                "poster.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes()
        );
        MockMultipartHttpServletRequestBuilder request = multipart(baseUrl + "/update/id/1");
        request.with(httpRequest -> {
            httpRequest.setMethod("PUT");
            return httpRequest;
        });

        given(eventService.update(any(), any(), any())).willReturn(response);

        mockMvc.perform(request.file(eventPart).file(posterPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.title").value("Updated Concert"));

        then(eventService).should().update(eq(1L), eq(info), eq(posterPart));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 삭제")
    void event_delete() throws Exception {
        EventResponse response = eventResponse(1L, "IU Concert");

        given(eventService.delete(1L)).willReturn(response);

        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L));

        then(eventService).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 일괄 삭제")
    void event_delete_bulk() throws Exception {
        DeleteEventBulkRequest info = DeleteEventBulkRequest.builder()
                .eventIds(List.of(1L, 2L))
                .build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(eventService).should().deleteBulk(info);
    }

    private MockMultipartFile jsonPart(String name, Object value) throws Exception {
        return new MockMultipartFile(
                name,
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(value)
        );
    }

    private InsertEventRequest insertRequest() {
        return InsertEventRequest.builder()
                .artistName("IU")
                .title("IU Concert")
                .description("Concert description")
                .venue("KSPO Dome")
                .venueAddress("Seoul")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .saleStartAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .saleEndAt(LocalDateTime.of(2026, 9, 17, 23, 59))
                .cancelDeadlineAt(LocalDateTime.of(2026, 9, 17, 17, 0))
                .runningMinutes(120)
                .ageLimit(12)
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();
    }

    private UpdateEventRequest updateRequest() {
        return UpdateEventRequest.builder()
                .title("Updated Concert")
                .description("Updated description")
                .venue("Olympic Stadium")
                .venueAddress("Seoul")
                .eventDateTime(LocalDateTime.of(2026, 9, 19, 18, 0))
                .runningMinutes(130)
                .ageLimit(12)
                .totalSeats(15000)
                .maxTicketsPerPerson(2)
                .build();
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
