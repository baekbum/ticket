package dev.bum.admin_service.controller.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.event.EventServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.InsertEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventCommonRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventScheduleRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminEventController.class)
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private EventServiceClient eventServiceClient;

    private final String baseUrl = "/api/v1/event";

    @Test
    @DisplayName("인증 정보 없이 이벤트 조회 요청 시 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("다회차 이벤트 등록")
    void event_insert_bulk() throws Exception {
        InsertEventBulkRequest info = insertBulkRequest();
        MockMultipartFile eventPart = jsonPart("event", info);
        MockMultipartFile poster = new MockMultipartFile("posterImage", "poster.png", "image/png", "image".getBytes());
        given(eventServiceClient.insertBulk(any(), any())).willReturn(List.of(eventResponse()));

        mockMvc.perform(multipart(baseUrl + "/insert/bulk")
                        .file(eventPart)
                        .file(poster))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(1L));

        then(eventServiceClient).should().insertBulk(any(), eq(poster));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 이벤트 조회")
    void event_select_by_id() throws Exception {
        given(eventServiceClient.selectById(1L)).willReturn(eventResponse());

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("IU Concert"));

        then(eventServiceClient).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 이벤트 조회")
    void event_select_by_cond() throws Exception {
        EventCondRequest cond = EventCondRequest.builder().title("IU").build();
        CustomPageResponse<EventResponse> response = CustomPageResponse.of(List.of(eventResponse()), 10, 0, 1, 1);
        given(eventServiceClient.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("IU Concert"));

        then(eventServiceClient).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 JSON 수정")
    void event_update_json() throws Exception {
        UpdateEventRequest info = UpdateEventRequest.builder().title("IU Concert Updated").build();
        given(eventServiceClient.update(1L, info)).willReturn(eventResponse());

        mockMvc.perform(put(baseUrl + "/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(eventServiceClient).should().update(1L, info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 multipart 수정")
    void event_update_multipart() throws Exception {
        UpdateEventRequest info = UpdateEventRequest.builder().title("IU Concert Updated").build();
        MockMultipartFile eventPart = jsonPart("event", info);
        MockMultipartFile poster = new MockMultipartFile("posterImage", "poster.png", "image/png", "image".getBytes());
        given(eventServiceClient.update(eq(1L), any(), any())).willReturn(eventResponse());

        mockMvc.perform(multipart(baseUrl + "/update/id/1")
                        .file(eventPart)
                        .file(poster)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk());

        then(eventServiceClient).should().update(eq(1L), any(), eq(poster));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 삭제")
    void event_delete() throws Exception {
        given(eventServiceClient.delete(1L)).willReturn(eventResponse());

        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L));

        then(eventServiceClient).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 벌크 삭제")
    void event_delete_bulk() throws Exception {
        DeleteEventBulkRequest info = DeleteEventBulkRequest.builder().eventIds(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(eventServiceClient).should().deleteBulk(info);
    }

    private MockMultipartFile jsonPart(String name, Object value) throws Exception {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(value));
    }

    private InsertEventBulkRequest insertBulkRequest() {
        return InsertEventBulkRequest.builder()
                .common(InsertEventCommonRequest.builder()
                        .artistName("IU")
                        .title("IU Concert")
                        .eventGroupCode("IU_2026_ENCORE")
                        .venue("KSPO Dome")
                        .venueAddress("Seoul")
                        .runningMinutes(120)
                        .ageLimit(12)
                        .totalSeats(1000)
                        .maxTicketsPerPerson(1)
                        .ticketLimitScope(TicketLimitScope.PER_GROUP)
                        .genre(EventGenre.CONCERT)
                        .region(EventRegion.SEOUL)
                        .theme(EventTheme.IDOL)
                        .build())
                .schedules(List.of(
                        InsertEventScheduleRequest.builder()
                                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                                .saleStartAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                                .saleEndAt(LocalDateTime.of(2026, 9, 17, 23, 59))
                                .cancelDeadlineAt(LocalDateTime.of(2026, 9, 17, 17, 0))
                                .build()
                ))
                .build();
    }

    private EventResponse eventResponse() {
        return EventResponse.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .status(EventStatus.ON_SALE)
                .genre(EventGenre.CONCERT)
                .region(EventRegion.SEOUL)
                .theme(EventTheme.IDOL)
                .build();
    }
}
