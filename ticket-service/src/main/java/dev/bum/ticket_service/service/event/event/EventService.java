package dev.bum.ticket_service.service.event.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
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
public class EventService {

    private final EventRepository repository;

    /**
     * 판매 중인 공연만 사용자 화면에 노출되도록 ID로 조회한다.
     */
    @Transactional(readOnly = true)
    public EventResponse selectVisibleById(Long id) {
        Event event = repository.selectById(id);
        if (event.getStatus() != EventStatus.ON_SALE) {
            throw new EventNotExistException("노출 가능한 이벤트 정보가 존재하지 않습니다.");
        }
        return event.toResponse();
    }

    /**
     * 판매 중인 공연만 사용자 화면에 노출되도록 조건 검색한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<EventResponse> selectVisibleByCond(EventCondRequest cond) {
        cond.setStatus(EventStatus.ON_SALE);
        log.info("[SELECT VISIBLE] Info : {}", cond);
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<EventResponse> eventPage = repository.selectByCond(cond, pageRequest).map(Event::toResponse);

        return CustomPageResponse.of(
                eventPage.getContent(),
                eventPage.getSize(),
                eventPage.getNumber(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages()
        );
    }

    /**
     * 검색 조건에서 sort 옵션을 처리하기 위한 메서드
     * @param sorts
     * @return
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");

                if (infos.length == 2) {
                    String field = infos[0];
                    String direction = infos[1];
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

}
