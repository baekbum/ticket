package dev.bum.ticket_service.service.event;

import dev.bum.ticket_service.dto.EventDto;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
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
     * 공연 정보 등록
     * @param info
     * @return
     */
    public EventDto insert(InsertEventInfo info) {
        return new EventDto(repository.insert(info));
    }

    /**
     * ID로 공연 정보 조회
     * @param id
     * @return
     */
    @Transactional(readOnly = true)
    public EventDto selectById(Long id) {
        return new EventDto(repository.selectById(id));
    }

    /**
     * 조검을 통해 공연 정보 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public Page<EventDto> selectByCond(EventCond cond) {
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<Event> events = repository.selectByCond(cond, pageRequest);

        return events.map(EventDto::new);

    }

    /**
     * 공연 정보 수정
     * @param id
     * @param info
     * @return
     */
    public EventDto update(Long id, UpdateEventInfo info) {
        return new EventDto(repository.update(id, info));
    }

    /**
     * 공연 정보 삭제
     * @param id
     * @return
     */
    public EventDto delete(Long id) {
        return new EventDto(repository.delete(id));
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
