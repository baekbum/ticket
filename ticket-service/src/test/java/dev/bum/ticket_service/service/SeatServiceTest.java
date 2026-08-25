package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    private SeatService seatService;

    @Mock
    private SeatRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

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
