package dev.bum.ticket_service.service.event.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.service.event.file.FileStorageService;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private final EventRepository repository;
    private final FileStorageService fileStorageService;

    /**
     * 공연 정보 등록
     * @param info
     * @return
     */
    public EventResponse insert(InsertEventRequest info, MultipartFile posterImage) {
        log.info("[INSERT WITH POSTER] Info : {}", info.toString());

        Event event = repository.insert(info);
        String posterUrl = fileStorageService.saveEventPoster(event.getEventId(), posterImage);
        event.updatePosterUrl(posterUrl);

        return event.toResponse();
    }

    /**
     * ID로 공연 정보 조회
     * @param id
     * @return
     */
    @Transactional(readOnly = true)
    public EventResponse selectById(Long id) {
        log.info("[SELECT] EventId : {}", id);

        return repository.selectById(id).toResponse();
    }

    @Transactional(readOnly = true)
    public EventResponse selectVisibleById(Long id) {
        Event event = repository.selectById(id);
        if (event.getStatus() != EventStatus.ON_SALE) {
            throw new EventNotExistException("노출 가능한 이벤트 정보가 존재하지 않습니다.");
        }
        return event.toResponse();
    }

    /**
     * 조검을 통해 공연 정보 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<EventResponse> selectByCond(EventCondRequest cond) {
        log.info("[SELECT] Info : {}", cond.toString());

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

    @Transactional(readOnly = true)
    public CustomPageResponse<EventResponse> selectVisibleByCond(EventCondRequest cond) {
        cond.setStatus(EventStatus.ON_SALE);
        return selectByCond(cond);
    }

    /**
     * 공연 정보 수정
     * @param id
     * @param info
     * @return
     */
    public EventResponse update(Long id, UpdateEventRequest info) {
        log.info("[UPDATE] Id : {}, Info : {}", id, info);
        return repository.update(id, info).toResponse();
    }

    public EventResponse update(Long id, UpdateEventRequest info, MultipartFile posterImage) {
        log.info("[UPDATE WITH POSTER] Id : {}, Info : {}", id, info);

        Event event = repository.selectById(id);
        String previousPosterUrl = event.getPosterUrl();
        String newPosterUrl = fileStorageService.saveEventPoster(id, posterImage);

        if (newPosterUrl != null) {
            info.setPosterUrl(newPosterUrl);
        }

        event.update(info);

        if (newPosterUrl != null) {
            fileStorageService.deleteByPublicUrl(previousPosterUrl);
        }

        return event.toResponse();
    }

    /**
     * 공연 정보 삭제
     * @param id
     * @return
     */
    public EventResponse delete(Long id) {
        log.info("[DELETE] EventId : {}", id);

        return repository.delete(id).toResponse();
    }

    public void deleteBulk(DeleteEventBulkRequest info) {
        if (info.getEventIds() == null || info.getEventIds().isEmpty()) {
            throw new IllegalArgumentException("삭제할 이벤트 정보가 없습니다.");
        }

        log.info("[BULK DELETE] EventIds : {}", info.getEventIds());
        info.getEventIds().forEach(this::delete);
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
