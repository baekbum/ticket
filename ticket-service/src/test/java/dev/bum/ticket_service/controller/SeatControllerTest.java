package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.ticket_service.dto.SeatDto;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.security.JwtAuthenticationFilter;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.seat.SeatService;
import dev.bum.ticket_service.vo.seat.*;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(SeatController.class)
class SeatControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SeatService seatService;

    private String domain = "seat";
    private String apiVersion = "v1";

    @Test
    @DisplayName("토큰 값 오류")
    void token_invalid() throws Exception {
        SeatCond cond = SeatCond.builder()
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
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor")
                .rows(10)
                .cols(10)
                .price(168000)
                .build();

        InsertSeatAreaConfig r_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.R)
                .zone("1구역")
                .rows(10)
                .cols(10)
                .price(145000)
                .build();

        List<InsertSeatAreaConfig> insertSeatAreaConfigList = List.of(vip_seat, r_seat);

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(1L)
                .insertSeatAreaConfigs(insertSeatAreaConfigList)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(info)))
            .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 정보 등록 성공 시 200코드 반환")
    void seat_insert() throws Exception {
        InsertSeatAreaConfig vip_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.VIP)
                .zone("Floor")
                .rows(10)
                .cols(10)
                .price(168000)
                .build();

        InsertSeatAreaConfig r_seat = InsertSeatAreaConfig.builder()
                .grade(SeatGrade.R)
                .zone("1구역")
                .rows(10)
                .cols(10)
                .price(145000)
                .build();

        List<InsertSeatAreaConfig> insertSeatAreaConfigList = List.of(vip_seat, r_seat);

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(1L)
                .insertSeatAreaConfigs(insertSeatAreaConfigList)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 좌석 검색하기")
    void seat_select_by_id() throws Exception {
        Event event = Event.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(14500)
                .status(EventStatus.ON_SALE)
                .build();

        LocalDateTime time = LocalDateTime.now();

        SeatDto response = SeatDto.builder()
                .seatId(1L)
                .seatNumber("Floor구역-1열-1번")
                .grade(SeatGrade.VIP)
                .price(168000)
                .status(SeatStatus.AVAILABLE)
                .time(time)
                .eventId(event.getEventId())
                .artistName(event.getArtistName())
                .title(event.getTitle())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .build();

        long seatId = 1L;

        given(seatService.selectById(seatId)).willReturn(response);

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + seatId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(1L))
                .andExpect(jsonPath("$.seatNumber").value("Floor구역-1열-1번"))
                .andExpect(jsonPath("$.grade").value(SeatGrade.VIP.name()))
                .andExpect(jsonPath("$.price").value(168000))
                .andExpect(jsonPath("$.status").value(SeatStatus.AVAILABLE.name()))
                // 이벤트 관련
                .andExpect(jsonPath("$.eventId").value(1L))
                .andExpect(jsonPath("$.artistName").value("아이유"))
                .andExpect(jsonPath("$.title").value("아이유 콘서트"))
                .andExpect(jsonPath("$.venue").value("올림픽 체조 경기장"));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 좌석 검색하기")
    void seat_select_by_cond() throws Exception {
        Event event = Event.builder()
                .eventId(1L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDate(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(14500)
                .status(EventStatus.ON_SALE)
                .build();

        LocalDateTime time = LocalDateTime.now();

        SeatDto response_1 = SeatDto.builder()
                .seatId(1L)
                .seatNumber("Floor구역-1열-1번")
                .grade(SeatGrade.VIP)
                .price(168000)
                .status(SeatStatus.AVAILABLE)
                .time(time)
                .eventId(event.getEventId())
                .artistName(event.getArtistName())
                .title(event.getTitle())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .build();

        SeatDto response_2 = SeatDto.builder()
                .seatId(2L)
                .seatNumber("Floor구역-1열-2번")
                .grade(SeatGrade.VIP)
                .price(168000)
                .status(SeatStatus.AVAILABLE)
                .time(time)
                .eventId(event.getEventId())
                .artistName(event.getArtistName())
                .title(event.getTitle())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .build();

        SeatDto response_3 = SeatDto.builder()
                .seatId(3L)
                .seatNumber("28구역-1열-10번")
                .grade(SeatGrade.A)
                .price(145000)
                .status(SeatStatus.AVAILABLE)
                .time(time)
                .eventId(event.getEventId())
                .artistName(event.getArtistName())
                .title(event.getTitle())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .build();

        SeatCond cond = SeatCond.builder()
                .grade(SeatGrade.A)
                .build();

        List<SeatDto> dtoList = List.of(response_3);

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize());
        PageImpl<SeatDto> response = new PageImpl<>(dtoList, pageable, dtoList.size());

        given(seatService.selectByCond(cond)).willReturn(response);

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/select")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seatId").value(3L))
                .andExpect(jsonPath("$.content[0].seatNumber").value("28구역-1열-10번"))
                .andExpect(jsonPath("$.content[0].grade").value(SeatGrade.A.name()))
                .andExpect(jsonPath("$.content[0].price").value(145000))
                .andExpect(jsonPath("$.content[0].status").value(SeatStatus.AVAILABLE.name()))
                .andExpect(jsonPath("$.content[0].eventId").value(1L))
                .andExpect(jsonPath("$.content[0].artistName").value("아이유"))
                .andExpect(jsonPath("$.content[0].title").value("아이유 콘서트"))
                .andExpect(jsonPath("$.content[0].venue").value("올림픽 체조 경기장"));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 정보 수정하기")
    void seat_update() throws Exception {

        UpdateSeatAreaConfig config_1 = UpdateSeatAreaConfig.builder()
                .id(1L)
                .status(SeatStatus.LOCKED)
                .build();

        UpdateSeatAreaConfig config_2 = UpdateSeatAreaConfig.builder()
                .id(2L)
                .status(SeatStatus.LOCKED)
                .build();

        List<UpdateSeatAreaConfig> updateSeatAreaConfigList = List.of(config_1, config_2);

        UpdateSeatInfo info = UpdateSeatInfo.builder()
                .updateSeatAreaConfigs(updateSeatAreaConfigList)
                .build();

        mockMvc.perform(put("/api/" + apiVersion + "/" + domain + "/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 정보 삭제하기")
    void seat_delete() throws Exception {
        long seatId = 1L;

        mockMvc.perform(delete("/api/" + apiVersion + "/" + domain + "/delete/id/" + seatId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}