package dev.bum.admin_service.controller.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.seat.SeatServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminSeatController.class)
class AdminSeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SeatServiceClient seatServiceClient;

    private final String baseUrl = "/api/v1/seat";

    @Test
    @DisplayName("인증 정보 없이 좌석 조회 요청 시 4xx 응답")
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

        then(seatServiceClient).should().insert(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("ID로 좌석 조회")
    void seat_select_by_id() throws Exception {
        given(seatServiceClient.selectById(1L)).willReturn(seatResponse());

        mockMvc.perform(get(baseUrl + "/select/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(1L));

        then(seatServiceClient).should().selectById(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("조건으로 좌석 조회")
    void seat_select_by_cond() throws Exception {
        SeatCondRequest cond = SeatCondRequest.builder().eventId(1L).build();
        CustomPageResponse<SeatResponse> response = CustomPageResponse.of(List.of(seatResponse()), 10, 0, 1, 1);
        given(seatServiceClient.selectByCond(any())).willReturn(response);

        mockMvc.perform(post(baseUrl + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seatId").value(1L));

        then(seatServiceClient).should().selectByCond(cond);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 수정")
    void seat_update() throws Exception {
        UpdateSeatRequest info = UpdateSeatRequest.builder()
                .updateSeatAreaConfigs(List.of(UpdateSeatAreaConfig.builder().id(1L).status(SeatStatus.LOCKED).build()))
                .build();

        mockMvc.perform(put(baseUrl + "/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatServiceClient).should().update(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 삭제")
    void seat_delete() throws Exception {
        mockMvc.perform(delete(baseUrl + "/delete/id/1"))
                .andExpect(status().isOk());

        then(seatServiceClient).should().delete(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 ID 목록 삭제")
    void seat_delete_by_id_list() throws Exception {
        DeleteSeatRequest info = DeleteSeatRequest.builder().seatIdList(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatServiceClient).should().deleteBySeatIdList(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 벌크 삭제")
    void seat_delete_bulk() throws Exception {
        DeleteSeatRequest info = DeleteSeatRequest.builder().seatIdList(List.of(1L, 2L)).build();

        mockMvc.perform(delete(baseUrl + "/delete/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatServiceClient).should().deleteBulk(info);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 기준 좌석 삭제")
    void seat_delete_by_area_id() throws Exception {
        mockMvc.perform(delete(baseUrl + "/delete/area/1"))
                .andExpect(status().isOk());

        then(seatServiceClient).should().deleteByAreaId(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 좌석 캐시 warm-up")
    void seat_warm_up_event() throws Exception {
        given(seatServiceClient.warmUpEventSeats(1L, SeatCacheWarmUpMode.OVERWRITE)).willReturn("warmed");

        mockMvc.perform(post(baseUrl + "/cache/warm-up/event/1")
                        .param("mode", "OVERWRITE"))
                .andExpect(status().isOk())
                .andExpect(content().string("warmed"));

        then(seatServiceClient).should().warmUpEventSeats(1L, SeatCacheWarmUpMode.OVERWRITE);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 좌석 캐시 warm-up")
    void seat_warm_up_area() throws Exception {
        given(seatServiceClient.warmUpAreaSeats(1L, SeatCacheWarmUpMode.MISSING_ONLY)).willReturn("warmed");

        mockMvc.perform(post(baseUrl + "/cache/warm-up/area/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("warmed"));

        then(seatServiceClient).should().warmUpAreaSeats(1L, SeatCacheWarmUpMode.MISSING_ONLY);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("이벤트 좌석 캐시 삭제")
    void seat_delete_event_cache() throws Exception {
        given(seatServiceClient.deleteEventSeatCache(1L)).willReturn("deleted");

        mockMvc.perform(delete(baseUrl + "/cache/event/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("deleted"));

        then(seatServiceClient).should().deleteEventSeatCache(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("구역 좌석 캐시 삭제")
    void seat_delete_area_cache() throws Exception {
        given(seatServiceClient.deleteAreaSeatCache(1L)).willReturn("deleted");

        mockMvc.perform(delete(baseUrl + "/cache/area/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("deleted"));

        then(seatServiceClient).should().deleteAreaSeatCache(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 캐시 잠금 테스트")
    void seat_lock_cache() throws Exception {
        given(seatServiceClient.lockSeatCacheForCurrentUser(1L)).willReturn("locked");

        mockMvc.perform(post(baseUrl + "/cache/seat/1/test-lock"))
                .andExpect(status().isOk())
                .andExpect(content().string("locked"));

        then(seatServiceClient).should().lockSeatCacheForCurrentUser(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 캐시 잠금 해제 테스트")
    void seat_unlock_cache() throws Exception {
        given(seatServiceClient.unlockSeatCache(1L)).willReturn("unlocked");

        mockMvc.perform(post(baseUrl + "/cache/seat/1/test-unlock"))
                .andExpect(status().isOk())
                .andExpect(content().string("unlocked"));

        then(seatServiceClient).should().unlockSeatCache(1L);
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("좌석 점유")
    void seat_occupy() throws Exception {
        SeatOccupyRequest info = SeatOccupyRequest.builder()
                .eventId(1L)
                .userId("user01")
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()))
                .maxTicketsPerPerson(4)
                .build();

        mockMvc.perform(post(baseUrl + "/occupy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());

        then(seatServiceClient).should().occupySeat(info);
    }

    private InsertSeatRequest insertRequest() {
        return InsertSeatRequest.builder()
                .eventId(1L)
                .areaId(1L)
                .insertSeatAreaConfigs(List.of(InsertSeatAreaConfig.builder()
                        .grade(SeatGrade.VIP)
                        .zone("VIP")
                        .rows(1)
                        .cols(2)
                        .price(150000)
                        .build()))
                .build();
    }

    private SeatResponse seatResponse() {
        return SeatResponse.builder()
                .seatId(1L)
                .eventId(1L)
                .areaId(1L)
                .zone("VIP")
                .seatRow(1)
                .seatCol(1)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
