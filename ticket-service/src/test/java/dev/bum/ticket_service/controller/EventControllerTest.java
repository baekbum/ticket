package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.event.event.EventService;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private String domain = "event";
    private String apiVersion = "v1";
    private DateTimeFormatter eventFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");

    @Test
    @DisplayName("토큰 값 오류")
    void token_invalid() throws Exception {
        EventCondRequest cond = EventCondRequest.builder()
                .page(0)
                .size(10)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("권한이 USER인 경우 403 코드 반환")
    void with_auth_user() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 9, 18, 18, 0);

        InsertEventRequest eventInfo = InsertEventRequest.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime)
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventInfo)))
            .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 등록 성공 시 200코드 반환")
    void event_insert() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 9, 18, 18, 0);

        InsertEventRequest eventInfo = InsertEventRequest.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime)
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        EventResponse response = EventResponse.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime.format(this.eventFormatter))
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        given(eventService.insert(any())).willReturn(response);

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventInfo)))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 이벤트 검색하기")
    void event_select_by_id() throws Exception {
        LocalDateTime eventDateTime = LocalDateTime.of(2026, 9, 18, 18, 0);

        EventResponse response = EventResponse.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime.format(this.eventFormatter))
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        given(eventService.selectById(any())).willReturn(response);

        long eventId = 1L;

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistName").value("아이유"))
                .andExpect(jsonPath("$.title").value("아이유 콘서트"))
                .andExpect(jsonPath("$.description").value("올림픽 체조 경기장에서 하는 아이유 콘서트"))
                .andExpect(jsonPath("$.venue").value("올림픽 체조 경기장"))
                .andExpect(jsonPath("$.eventDateTime").value(eventDateTime.format(this.eventFormatter)))
                .andExpect(jsonPath("$.totalSeats").value(14500))
                .andExpect(jsonPath("$.maxTicketsPerPerson").value(4));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 이벤트 검색하기")
    void event_select_by_cond() throws Exception {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("아이유")
                .build();

        LocalDateTime eventDateTime_1 = LocalDateTime.of(2024, 9, 16, 17,0);
        LocalDateTime eventDateTime_2 = LocalDateTime.of(2026, 9, 16, 18,0);

        EventResponse response_1 = EventResponse.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("상암 월드컵 경기장에서 하는 아이유 콘서트")
                .venue("상암 월드컵 경기장")
                .eventDateTime(eventDateTime_1.format(this.eventFormatter))
                .totalSeats(60000)
                .maxTicketsPerPerson(4)
                .build();

        EventResponse response_2 = EventResponse.builder()
                .eventId(2L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime_2.format(this.eventFormatter))
                .totalSeats(14500)
                .maxTicketsPerPerson(1)
                .build();

        List<EventResponse> dtoList = List.of(response_1, response_2);

        CustomPageResponse<EventResponse> response = CustomPageResponse.of(
                dtoList,
                cond.getSize(),
                cond.getPage(),
                dtoList.size(),
                1
        );

        given(eventService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(1L))
                .andExpect(jsonPath("$.content[1].eventId").value(2L))
                .andExpect(jsonPath("$.content[0].artistName").value("아이유"))
                .andExpect(jsonPath("$.content[1].artistName").value("아이유"))
                .andExpect(jsonPath("$.content[0].venue").value("상암 월드컵 경기장"))
                .andExpect(jsonPath("$.content[1].venue").value("올림픽 체조 경기장"));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 정보 수정")
    void event_update() throws Exception {
        long eventId = 1L;

        LocalDateTime eventDateTime = LocalDateTime.of(2024, 5, 16, 17, 0);

        UpdateEventRequest info = UpdateEventRequest.builder()
                .title("수정된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime)
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        EventResponse response = EventResponse.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("수정된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime.format(this.eventFormatter))
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        given(eventService.update(eventId, info)).willReturn(response);

        mockMvc.perform(put("/api/" + apiVersion + "/" + domain + "/update/id/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.artistName").value("아이유"))
                .andExpect(jsonPath("$.title").value("수정된 아이유 콘서트"))
                .andExpect(jsonPath("$.description").value("올림픽 체조 경기장에서 하는 아이유 콘서트"))
                .andExpect(jsonPath("$.venue").value("올림픽 체조 경기장"))
                .andExpect(jsonPath("$.eventDateTime").value(eventDateTime.format(this.eventFormatter)))
                .andExpect(jsonPath("$.totalSeats").value(14500))
                .andExpect(jsonPath("$.maxTicketsPerPerson").value(4));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 정보 삭제")
    void event_delete() throws Exception {
        long eventId = 1L;

        LocalDateTime eventDateTime = LocalDateTime.of(2024, 5, 16, 17, 0);

        EventResponse response = EventResponse.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("삭제된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(eventDateTime.format(this.eventFormatter))
                .totalSeats(14500)
                .maxTicketsPerPerson(4)
                .build();

        given(eventService.delete(1L)).willReturn(response);

        mockMvc.perform(delete("/api/" + apiVersion + "/" + domain + "/delete/id/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.artistName").value("아이유"))
                .andExpect(jsonPath("$.title").value("삭제된 아이유 콘서트"))
                .andExpect(jsonPath("$.description").value("올림픽 체조 경기장에서 하는 아이유 콘서트"))
                .andExpect(jsonPath("$.venue").value("올림픽 체조 경기장"))
                .andExpect(jsonPath("$.eventDateTime").value(eventDateTime.format(this.eventFormatter)))
                .andExpect(jsonPath("$.totalSeats").value(14500))
                .andExpect(jsonPath("$.maxTicketsPerPerson").value(4));
    }
}