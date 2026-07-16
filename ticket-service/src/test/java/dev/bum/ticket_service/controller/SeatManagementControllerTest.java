package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.seat.dto.DeleteSeatRequest;
import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.dto.UpdateSeatRequest;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import dev.bum.ticket_service.controller.seat.SeatManagementController;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.seat.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(SeatManagementController.class)
class SeatManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SeatService seatService;

    private final String baseUrl = "/api/v1/manage/seat";

    @Test
    @DisplayName("인증 없이 관리자용 좌석 조회를 요청하면 4xx 응답")
    void token_invalid() throws Exception {
        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 등록")
    void seat_insert() throws Exception {
        InsertSeatRequest info = insertRequest();

        mockMvc.perform(post(baseUrl + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatService).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 좌석 조회")
    void seat_select_by_id() throws Exception {
        given(seatService.selectById(1L)).willReturn(seatResponse());

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(1L));

        then(seatService).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 좌석 조회")
    void seat_select_by_cond() throws Exception {
        SeatCondRequest cond = SeatCondRequest.builder().eventId(1L).build();
        CustomPageResponse<SeatResponse> response = CustomPageResponse.of(List.of(seatResponse()), 10, 0, 1, 1);
        given(seatService.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seatId").value(1L));

        then(seatService).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 수정")
    void seat_update() throws Exception {
        UpdateSeatRequest info = updateRequest();

        mockMvc.perform(put(baseUrl + "/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatService).should().update(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 삭제")
    void seat_delete() throws Exception {
        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk());

        then(seatService).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 목록 삭제")
    void seat_delete_by_id_list() throws Exception {
        DeleteSeatRequest info = DeleteSeatRequest.builder().seatIdList(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatService).should().deleteBySeatIdList(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 기준 좌석 삭제")
    void seat_delete_by_area_id() throws Exception {
        mockMvc.perform(delete(baseUrl + "/delete/area/1"))
                .andExpect(status().isOk());

        then(seatService).should().deleteByAreaId(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 좌석 캐시 warm-up")
    void warm_up_event_seats() throws Exception {
        given(seatService.warmUpEventSeatsToCache(1L, SeatCacheWarmUpMode.OVERWRITE)).willReturn("warmed");

        mockMvc.perform(post(baseUrl + "/cache/warm-up/event/1")
                        .param("mode", "OVERWRITE"))
                .andExpect(status().isOk())
                .andExpect(content().string("warmed"));

        then(seatService).should().warmUpEventSeatsToCache(1L, SeatCacheWarmUpMode.OVERWRITE);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 좌석 캐시 삭제")
    void delete_event_seat_cache() throws Exception {
        given(seatService.deleteEventSeatsFromCache(1L)).willReturn("deleted");

        mockMvc.perform(delete(baseUrl + "/cache/event/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("deleted"));

        then(seatService).should().deleteEventSeatsFromCache(1L);
    }

    @Test
    @DisplayName("좌석 캐시 잠금 테스트")
    void lock_seat_cache_for_current_user() throws Exception {
        given(seatService.lockSeatCacheForUser(1L, "admin")).willReturn("locked");

        mockMvc.perform(post(baseUrl + "/cache/seat/1/test-lock")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(content().string("locked"));

        then(seatService).should().lockSeatCacheForUser(1L, "admin");
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 점유")
    void occupy_seat() throws Exception {
        SeatOccupyRequest request = occupyRequest("user01");
        SeatOccupyResponse response = SeatOccupyResponse.builder()
                .orderId("order-id")
                .eventId(1L)
                .userId("user01")
                .seats(request.getSeats())
                .expiresAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        given(seatService.occupySeat(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/occupy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-id"));

        then(seatService).should().occupySeat(request);
    }

    private InsertSeatRequest insertRequest() {
        return InsertSeatRequest.builder()
                .eventId(1L)
                .areaId(1L)
                .insertSeatAreaConfigs(List.of(InsertSeatAreaConfig.builder()
                        .grade(SeatGrade.VIP)
                        .zone("VIP")
                        .rows(2)
                        .cols(2)
                        .price(150000)
                        .startX(10D)
                        .startY(20D)
                        .build()))
                .build();
    }

    private UpdateSeatRequest updateRequest() {
        return UpdateSeatRequest.builder()
                .updateSeatAreaConfigs(List.of(UpdateSeatAreaConfig.builder()
                        .id(1L)
                        .status(SeatStatus.LOCKED)
                        .price(160000)
                        .build()))
                .build();
    }

    private SeatOccupyRequest occupyRequest(String userId) {
        return SeatOccupyRequest.builder()
                .eventId(1L)
                .userId(userId)
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()))
                .maxTicketsPerPerson(4)
                .build();
    }

    private SeatResponse seatResponse() {
        return SeatResponse.builder()
                .seatId(1L)
                .zone("VIP")
                .seatRow(1)
                .seatCol(1)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .eventId(1L)
                .areaId(1L)
                .build();
    }

    private UsernamePasswordAuthenticationToken adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
