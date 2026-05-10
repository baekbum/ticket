package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.ticket_service.dto.EventDto;
import dev.bum.ticket_service.security.JwtAuthenticationFilter;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.event.EventService;
import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
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

    @Test
    @DisplayName("토큰 값 오류")
    void token_invalid() throws Exception {
        EventCond cond = EventCond.builder()
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
        InsertEventInfo eventInfo = InsertEventInfo.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18,0))
                .totalSeats(14500)
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
        InsertEventInfo eventInfo = InsertEventInfo.builder()
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18,0))
                .totalSeats(14500)
                .build();

        EventDto response = EventDto.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18,0))
                .totalSeats(14500)
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
        EventDto response = EventDto.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18,0))
                .totalSeats(14500)
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
                .andExpect(jsonPath("$.eventDate").value("2026-09-18T18:00:00"))
                .andExpect(jsonPath("$.totalSeats").value(14500));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 이벤트 검색하기")
    void event_select_by_cond() throws Exception {
        EventCond cond = EventCond.builder()
                .artistName("아이유")
                .build();

        EventDto response_1 = EventDto.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("상암 월드컵 경기장에서 하는 아이유 콘서트")
                .venue("상암 월드컵 경기장")
                .eventDate(LocalDateTime.of(2024, 9, 16, 17,0))
                .totalSeats(60000)
                .build();

        EventDto response_2 = EventDto.builder()
                .eventId(2L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 16, 18,0))
                .totalSeats(14500)
                .build();

        List<EventDto> dtoList = List.of(response_1, response_2);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        PageImpl<EventDto> response = new PageImpl<>(dtoList, pageable, dtoList.size());

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

        UpdateEventInfo info = UpdateEventInfo.builder()
                .title("수정된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2024, 5, 16, 17, 0))
                .totalSeats(14500)
                .build();

        EventDto response = EventDto.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("수정된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 5, 16, 17,0))
                .totalSeats(14500)
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
                .andExpect(jsonPath("$.eventDate").value("2026-05-16T17:00:00"))
                .andExpect(jsonPath("$.totalSeats").value(14500));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 정보 삭제")
    void event_delete() throws Exception {
        long eventId = 1L;

        EventDto response = EventDto.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("삭제된 아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 5, 16, 17,0))
                .totalSeats(14500)
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
                .andExpect(jsonPath("$.eventDate").value("2026-05-16T17:00:00"))
                .andExpect(jsonPath("$.totalSeats").value(14500));
    }
}