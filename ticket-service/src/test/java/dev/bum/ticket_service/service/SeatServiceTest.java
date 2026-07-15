package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
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
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.seat.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    private SeatService seatService;

    @Mock
    private SeatRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

    @Test
    @DisplayName("좌석 등록")
    void insert() {
        InsertSeatRequest info = insertRequest();

        seatService.insert(info);

        then(repository).should().insert(info);
    }

    @Test
    @DisplayName("이벤트 기준 좌석 수 조회")
    void count_by_event_id() {
        given(repository.countByEventId(1L)).willReturn(4L);

        long count = seatService.countByEventId(1L);

        assertThat(count).isEqualTo(4L);
        then(repository).should().countByEventId(1L);
    }

    @Test
    @DisplayName("ID로 좌석 조회")
    void select_by_id() {
        Seat seat = seat(1L, "VIP", 1, 1);

        given(repository.selectById(1L)).willReturn(seat);

        SeatResponse response = seatService.selectById(1L);

        assertThat(response.getSeatId()).isEqualTo(1L);
        assertThat(response.getZone()).isEqualTo("VIP");
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 좌석 조회")
    void select_by_cond() {
        SeatCondRequest cond = SeatCondRequest.builder()
                .eventId(1L)
                .sort(List.of("seatId-desc"))
                .build();
        Page<Seat> page = new PageImpl<>(List.of(seat(1L, "VIP", 1, 1)), PageRequest.of(0, 10), 1);

        given(repository.selectByCond(argThat(request -> request.getEventId().equals(1L)), argThat(pageable ->
                pageable.getSort().getOrderFor("seatId") != null
                        && pageable.getSort().getOrderFor("seatId").isDescending()
        ))).willReturn(page);

        CustomPageResponse<SeatResponse> response = seatService.selectByCond(cond);

        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getSeatId()).isEqualTo(1L);
        then(repository).should().selectByCond(argThat(request -> request.getEventId().equals(1L)), argThat(pageable ->
                pageable.getSort().getOrderFor("seatId") != null
                        && pageable.getSort().getOrderFor("seatId").isDescending()
        ));
    }

    @Test
    @DisplayName("좌석 수정")
    void update() {
        UpdateSeatRequest info = updateRequest();

        seatService.update(info);

        then(repository).should().update(info);
    }

    @Test
    @DisplayName("좌석 삭제")
    void delete() {
        seatService.delete(1L);

        then(repository).should().delete(1L);
    }

    @Test
    @DisplayName("좌석 ID 목록 삭제")
    void delete_by_seat_id_list() {
        DeleteSeatRequest info = DeleteSeatRequest.builder()
                .seatIdList(List.of(1L, 2L))
                .build();

        seatService.deleteBySeatIdList(info);

        then(repository).should().deleteByIdList(List.of(1L, 2L));
    }

    @Test
    @DisplayName("좌석 ID 목록이 비어있으면 삭제하지 않음")
    void delete_by_empty_seat_id_list() {
        DeleteSeatRequest info = DeleteSeatRequest.builder()
                .seatIdList(List.of())
                .build();

        seatService.deleteBySeatIdList(info);

        then(repository).should(never()).deleteByIdList(any());
    }

    @Test
    @DisplayName("구역 기준 좌석 삭제")
    void delete_by_area_id() {
        seatService.deleteByAreaId(1L);

        then(repository).should().deleteByAreaId(1L);
    }

    @Test
    @DisplayName("이벤트 좌석 캐시 warm-up 위임")
    void warm_up_event_seats_to_cache() {
        given(seatCacheService.warmUpEventSeatsToCache(1L, SeatCacheWarmUpMode.OVERWRITE)).willReturn("warmed");

        String response = seatService.warmUpEventSeatsToCache(1L, SeatCacheWarmUpMode.OVERWRITE);

        assertThat(response).isEqualTo("warmed");
        then(seatCacheService).should().warmUpEventSeatsToCache(1L, SeatCacheWarmUpMode.OVERWRITE);
    }

    @Test
    @DisplayName("구역 좌석 캐시 warm-up 위임")
    void warm_up_area_seats_to_cache() {
        given(seatCacheService.warmUpAreaSeatsToCache(1L, SeatCacheWarmUpMode.MISSING_ONLY)).willReturn("warmed");

        String response = seatService.warmUpAreaSeatsToCache(1L, SeatCacheWarmUpMode.MISSING_ONLY);

        assertThat(response).isEqualTo("warmed");
        then(seatCacheService).should().warmUpAreaSeatsToCache(1L, SeatCacheWarmUpMode.MISSING_ONLY);
    }

    @Test
    @DisplayName("이벤트 좌석 캐시 삭제 위임")
    void delete_event_seats_from_cache() {
        given(seatCacheService.deleteEventSeatsFromCache(1L)).willReturn("deleted");

        String response = seatService.deleteEventSeatsFromCache(1L);

        assertThat(response).isEqualTo("deleted");
        then(seatCacheService).should().deleteEventSeatsFromCache(1L);
    }

    @Test
    @DisplayName("구역 좌석 캐시 삭제 위임")
    void delete_area_seats_from_cache() {
        given(seatCacheService.deleteAreaSeatsFromCache(1L)).willReturn("deleted");

        String response = seatService.deleteAreaSeatsFromCache(1L);

        assertThat(response).isEqualTo("deleted");
        then(seatCacheService).should().deleteAreaSeatsFromCache(1L);
    }

    @Test
    @DisplayName("좌석 캐시 잠금 위임")
    void lock_seat_cache_for_user() {
        given(seatCacheService.lockSeatCacheForUser(1L, "user01")).willReturn("locked");

        String response = seatService.lockSeatCacheForUser(1L, "user01");

        assertThat(response).isEqualTo("locked");
        then(seatCacheService).should().lockSeatCacheForUser(1L, "user01");
    }

    @Test
    @DisplayName("좌석 캐시 잠금 해제 위임")
    void unlock_seat_cache() {
        given(seatCacheService.unlockSeatCache(1L)).willReturn("unlocked");

        String response = seatService.unlockSeatCache(1L);

        assertThat(response).isEqualTo("unlocked");
        then(seatCacheService).should().unlockSeatCache(1L);
    }

    @Test
    @DisplayName("좌석 점유 위임")
    void occupy_seat() {
        SeatOccupyRequest request = SeatOccupyRequest.builder()
                .eventId(1L)
                .userId("user01")
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()))
                .maxTicketsPerPerson(4)
                .build();
        SeatOccupyResponse expected = SeatOccupyResponse.builder()
                .orderId("order-id")
                .eventId(1L)
                .userId("user01")
                .seats(request.getSeats())
                .expiresAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();

        given(seatCacheService.occupySeat(request)).willReturn(expected);

        SeatOccupyResponse response = seatService.occupySeat(request);

        assertThat(response.getOrderId()).isEqualTo("order-id");
        then(seatCacheService).should().occupySeat(request);
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

    private Seat seat(Long seatId, String zone, Integer row, Integer col) {
        Event event = Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .build();

        return Seat.builder()
                .seatId(seatId)
                .event(event)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
