package dev.bum.ticket_service.service.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaRepository;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayout;
import dev.bum.ticket_service.jpa.event.eventLayout.EventLayoutJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository repository;
    private final EventLayoutJpaRepository layoutJpaRepository;

    /**
     * 이벤트 ID로 저장된 구역 배치도 SVG를 조회한다.
     */
    @Transactional(readOnly = true)
    public EventLayoutResponse selectLayout(Long eventId) {
        log.info("[EVENT LAYOUT SELECT] eventId : {}", eventId);
        return layoutJpaRepository.findByEvent_EventId(eventId)
                .map(EventLayout::toResponse)
                .orElse(null);
    }

    /**
     * 구역 ID로 단건 구역 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public AreaResponse selectById(Long id) {
        log.info("[AREA SELECT] areaId : {}", id);
        return repository.selectById(id).toResponse();
    }

    /**
     * 검색 조건과 페이징 조건으로 구역 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<AreaResponse> selectByCond(AreaCondRequest cond) {
        log.info("[AREA SELECT] cond : {}", cond);

        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<AreaResponse> areaPage = repository.selectByCond(cond, pageRequest).map(Area::toResponse);

        return CustomPageResponse.of(
                areaPage.getContent(),
                areaPage.getSize(),
                areaPage.getNumber(),
                areaPage.getTotalElements(),
                areaPage.getTotalPages()
        );
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환한다.
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");
                if (infos.length == 2) {
                    orders.add(new Sort.Order(Sort.Direction.fromString(infos[1]), infos[0]));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }
}
