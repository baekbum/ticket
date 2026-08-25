package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayout;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayoutJpaRepository;
import dev.bum.ticket_service.service.area.AreaService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @InjectMocks
    private AreaService areaService;

    @Mock
    private AreaRepository repository;

    @Mock
    private EventLayoutJpaRepository layoutJpaRepository;

    @Test
    @DisplayName("이벤트 레이아웃 조회")
    void select_layout() {
        EventLayout layout = EventLayout.builder()
                .layoutId(1L)
                .event(event())
                .originalFileName("layout.svg")
                .svgText("<svg/>")
                .build();

        given(layoutJpaRepository.findByEvent_EventId(1L)).willReturn(Optional.of(layout));

        EventLayoutResponse response = areaService.selectLayout(1L);

        assertThat(response.getLayoutId()).isEqualTo(1L);
        assertThat(response.getSvgText()).isEqualTo("<svg/>");
        then(layoutJpaRepository).should().findByEvent_EventId(1L);
    }

    @Test
    @DisplayName("이벤트 레이아웃이 없으면 null 반환")
    void select_layout_empty() {
        given(layoutJpaRepository.findByEvent_EventId(1L)).willReturn(Optional.empty());

        EventLayoutResponse response = areaService.selectLayout(1L);

        assertThat(response).isNull();
        then(layoutJpaRepository).should().findByEvent_EventId(1L);
    }

    @Test
    @DisplayName("ID로 구역 조회")
    void select_by_id() {
        given(repository.selectById(1L)).willReturn(area(1L, "VIP"));

        AreaResponse response = areaService.selectById(1L);

        assertThat(response.getAreaName()).isEqualTo("VIP");
        assertThat(response.getLayoutKey()).isEqualTo("VIP");
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 구역 조회")
    void select_by_cond() {
        AreaCondRequest cond = AreaCondRequest.builder()
                .eventId(1L)
                .sort(List.of("areaId-desc"))
                .build();
        Page<Area> page = new PageImpl<>(List.of(area(1L, "VIP")), PageRequest.of(0, 10), 1);

        given(repository.selectByCond(argThat(request -> request.getEventId().equals(1L)), argThat(pageable ->
                pageable.getSort().getOrderFor("areaId") != null
                        && pageable.getSort().getOrderFor("areaId").isDescending()
        ))).willReturn(page);

        CustomPageResponse<AreaResponse> response = areaService.selectByCond(cond);

        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(repository).should().selectByCond(argThat(request -> request.getEventId().equals(1L)), argThat(pageable ->
                pageable.getSort().getOrderFor("areaId") != null
                        && pageable.getSort().getOrderFor("areaId").isDescending()
        ));
    }

    private Area area(Long areaId, String areaName) {
        return Area.builder()
                .areaId(areaId)
                .event(event())
                .areaName(areaName)
                .layoutKey(areaName)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(AreaStatus.ACTIVE)
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .title("IU Concert")
                .artistName("IU")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(1000)
                .availableSeats(1000)
                .maxTicketsPerPerson(4)
                .build();
    }
}
