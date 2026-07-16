package dev.bum.ticket_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.area.dto.DeleteAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaBulkRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaJsonRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.exception.area.AreaLayoutAlreadyExistsException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayout;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayoutJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.service.area.AreaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @InjectMocks
    private AreaService areaService;

    @Mock
    private AreaRepository repository;

    @Mock
    private AreaJpaRepository areaJpaRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventLayoutJpaRepository layoutJpaRepository;

    @Mock
    private SeatJpaRepository seatJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("구역 등록")
    void insert() {
        InsertAreaRequest info = insertRequest("VIP");
        Area area = area(1L, "VIP");

        given(repository.insert(info)).willReturn(area);

        AreaResponse response = areaService.insert(info);

        assertThat(response.getAreaName()).isEqualTo("VIP");
        then(repository).should().insert(info);
    }

    @Test
    @DisplayName("구역 벌크 등록")
    void insert_bulk() {
        InsertAreaRequest vip = insertRequest("VIP");
        InsertAreaRequest r = insertRequest("R");
        InsertAreaBulkRequest info = InsertAreaBulkRequest.builder()
                .areas(List.of(vip, r))
                .build();

        given(repository.insert(vip)).willReturn(area(1L, "VIP"));
        given(repository.insert(r)).willReturn(area(2L, "R"));

        List<AreaResponse> response = areaService.insertBulk(info);

        assertThat(response).hasSize(2);
        then(repository).should().insert(vip);
        then(repository).should().insert(r);
    }

    @Test
    @DisplayName("구역 벌크 등록 대상이 비어있으면 예외 발생")
    void insert_bulk_empty() {
        InsertAreaBulkRequest info = InsertAreaBulkRequest.builder()
                .areas(List.of())
                .build();

        assertThatThrownBy(() -> areaService.insertBulk(info))
                .isInstanceOf(IllegalArgumentException.class);

        then(repository).should(never()).insert(any());
    }

    @Test
    @DisplayName("JSON 텍스트로 구역 등록")
    void insert_json() throws Exception {
        InsertAreaRequest vip = insertRequest("VIP");
        InsertAreaJsonRequest info = InsertAreaJsonRequest.builder()
                .jsonText(objectMapper.writeValueAsString(List.of(vip)))
                .build();

        given(repository.insert(argThat(area -> "VIP".equals(area.getAreaName())))).willReturn(area(1L, "VIP"));

        List<AreaResponse> response = areaService.insertJson(info);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getAreaName()).isEqualTo("VIP");
        then(repository).should().insert(argThat(area -> "VIP".equals(area.getAreaName())));
    }

    @Test
    @DisplayName("잘못된 JSON 텍스트면 예외 발생")
    void insert_json_invalid() {
        InsertAreaJsonRequest info = InsertAreaJsonRequest.builder()
                .jsonText("{invalid")
                .build();

        assertThatThrownBy(() -> areaService.insertJson(info))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SVG 파일로 구역 등록")
    void insert_svg() {
        MockMultipartFile svgFile = svgFile();
        Event event = event();

        given(layoutJpaRepository.existsByEvent_EventId(1L)).willReturn(false);
        given(areaJpaRepository.existsByEvent_EventId(1L)).willReturn(false);
        given(eventRepository.selectById(1L)).willReturn(event);
        given(layoutJpaRepository.findByEvent_EventId(1L)).willReturn(Optional.empty());
        given(layoutJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(repository.insert(argThat(area -> area != null && "VIP".equals(area.getAreaName())))).willReturn(area(1L, "VIP"));
        given(repository.insert(argThat(area -> area != null && "R".equals(area.getAreaName())))).willReturn(area(2L, "R"));

        List<AreaResponse> response = areaService.insertSvg(1L, svgFile, false);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(AreaResponse::getAreaName).containsExactly("VIP", "R");
        then(eventRepository).should().selectById(1L);
        then(layoutJpaRepository).should().save(any(EventLayout.class));
        then(repository).should().insert(argThat(area -> area != null && "VIP".equals(area.getAreaName())
                && area.getGrade() == SeatGrade.VIP
                && area.getPrice().equals(150000)));
        then(repository).should().insert(argThat(area -> area != null && "R".equals(area.getAreaName())
                && area.getGrade() == SeatGrade.R
                && area.getPrice().equals(120000)));
    }

    @Test
    @DisplayName("기존 레이아웃이 있고 force가 false면 예외 발생")
    void insert_svg_fail_when_layout_exists() {
        given(layoutJpaRepository.existsByEvent_EventId(1L)).willReturn(true);

        assertThatThrownBy(() -> areaService.insertSvg(1L, svgFile(), false))
                .isInstanceOf(AreaLayoutAlreadyExistsException.class);

        then(repository).should(never()).insert(any());
    }

    @Test
    @DisplayName("force가 true면 기존 레이아웃 삭제 후 SVG 등록")
    void insert_svg_with_force() {
        MockMultipartFile svgFile = svgFile();
        Event event = event();

        given(layoutJpaRepository.existsByEvent_EventId(1L)).willReturn(true);
        given(eventRepository.selectById(1L)).willReturn(event);
        given(layoutJpaRepository.findByEvent_EventId(1L)).willReturn(Optional.empty());
        given(layoutJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(repository.insert(any())).willReturn(area(1L, "VIP"));

        areaService.insertSvg(1L, svgFile, true);

        then(seatJpaRepository).should().deleteByEventEventId(1L);
        then(areaJpaRepository).should().deleteByEvent_EventId(1L);
        then(layoutJpaRepository).should().deleteByEvent_EventId(1L);
        then(repository).should(times(2)).insert(any());
    }

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

    @Test
    @DisplayName("구역 수정")
    void update() {
        UpdateAreaRequest info = UpdateAreaRequest.builder()
                .areaName("VIP-A")
                .price(160000)
                .build();

        given(repository.update(1L, info)).willReturn(area(1L, "VIP-A"));

        AreaResponse response = areaService.update(1L, info);

        assertThat(response.getAreaName()).isEqualTo("VIP-A");
        then(repository).should().update(1L, info);
    }

    @Test
    @DisplayName("구역 삭제")
    void delete() {
        given(repository.delete(1L)).willReturn(area(1L, "VIP"));

        AreaResponse response = areaService.delete(1L);

        assertThat(response.getAreaId()).isEqualTo(1L);
        then(repository).should().delete(1L);
    }

    @Test
    @DisplayName("구역 벌크 삭제")
    void delete_bulk() {
        DeleteAreaBulkRequest info = DeleteAreaBulkRequest.builder()
                .areaIds(List.of(1L, 2L))
                .build();

        given(repository.delete(1L)).willReturn(area(1L, "VIP"));
        given(repository.delete(2L)).willReturn(area(2L, "R"));

        areaService.deleteBulk(info);

        then(repository).should().delete(1L);
        then(repository).should().delete(2L);
    }

    @Test
    @DisplayName("구역 벌크 삭제 대상이 비어있으면 예외 발생")
    void delete_bulk_empty() {
        DeleteAreaBulkRequest info = DeleteAreaBulkRequest.builder()
                .areaIds(List.of())
                .build();

        assertThatThrownBy(() -> areaService.deleteBulk(info))
                .isInstanceOf(IllegalArgumentException.class);

        then(repository).should(never()).delete(any());
    }

    private InsertAreaRequest insertRequest(String areaName) {
        return InsertAreaRequest.builder()
                .eventId(1L)
                .areaName(areaName)
                .grade("VIP".equals(areaName) ? SeatGrade.VIP : SeatGrade.R)
                .price("VIP".equals(areaName) ? 150000 : 120000)
                .status(AreaStatus.ACTIVE)
                .build();
    }

    private Area area(Long areaId, String areaName) {
        return Area.builder()
                .areaId(areaId)
                .event(event())
                .areaName(areaName)
                .grade("VIP".equals(areaName) || "VIP-A".equals(areaName) ? SeatGrade.VIP : SeatGrade.R)
                .price("VIP".equals(areaName) || "VIP-A".equals(areaName) ? 150000 : 120000)
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

    private MockMultipartFile svgFile() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <path id="area-vip-VIP" class="area vip" data-area-name="VIP" data-grade="VIP" data-price="150000"/>
                  <rect id="area-R" class="area r" data-area-name="R" data-grade="R" data-price="120000"/>
                  <path id="console" class="area console" data-area-name="CONSOLE"/>
                </svg>
                """;
        return new MockMultipartFile("svgFile", "layout.svg", "image/svg+xml", svg.getBytes());
    }
}
