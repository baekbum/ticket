package dev.bum.ticket_service.service.event.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.InsertEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventCommonRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventScheduleRequest;
import dev.bum.ticket_service.service.event.file.EventPosterService;
import dev.bum.ticket_service.service.event.policy.EventPolicyOutboxService;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
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
public class EventManagementService {

    // 공연 데이터를 조회하고 저장하는 레포지토리.
    private final EventRepository repository;
    // 공연 업무 처리를 담당하는 서비스.
    private final EventPosterService eventPosterService;
    // 공연 정책 업무 처리를 담당하는 서비스.
    private final EventPolicyOutboxService eventPolicyOutboxService;

    /**
     * 공통 공연 정보와 다중 회차 정보를 받아 여러 이벤트를 하나의 트랜잭션으로 등록한다.
     */
    @AuditLog(action = "EVENT_CREATE_BULK", targetType = "EVENT")
    public List<EventResponse> insertBulk(InsertEventBulkRequest info, MultipartFile posterImage) {

        if (info.getSchedules() == null || info.getSchedules().isEmpty()) {
            throw new IllegalArgumentException("등록할 공연 일정 정보가 없습니다.");
        }

        List<EventResponse> responses = new ArrayList<>();

        for (InsertEventScheduleRequest schedule : info.getSchedules()) {
            InsertEventRequest request = toInsertEventRequest(info.getCommon(), schedule);
            Event event = repository.insert(request);
            String posterUrl = eventPosterService.saveForTransaction(posterImage, null);
            event.updatePosterUrl(posterUrl);
            eventPolicyOutboxService.enqueue(event, false);
            responses.add(event.toResponse());
        }

        return responses;
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

    /**
     * 공연 정보 수정
     * @param id
     * @param info
     * @return
     */
    @AuditLog(action = "EVENT_UPDATE", targetType = "EVENT")
    public EventResponse update(Long id, UpdateEventRequest info) {

        log.info("[UPDATE] Id : {}, Info : {}", id, info);
        Event beforeEvent = repository.selectByIdForUpdate(id);
        AuditDataMapper.setChangedData(beforeEvent, info, "description", "venueAddress", "posterUrl");
        Event event = repository.update(id, info);
        eventPolicyOutboxService.enqueue(event, false);

        return event.toResponse();
    }

    /**
     * 포스터 이미지를 포함해 공연 정보를 수정하고 기존 포스터 파일을 정리한다.
     */
    @AuditLog(action = "EVENT_UPDATE", targetType = "EVENT")
    public EventResponse update(Long id, UpdateEventRequest info, MultipartFile posterImage) {

        log.info("[UPDATE WITH POSTER] Id : {}, Info : {}", id, info);

        Event event = repository.selectByIdForUpdate(id);
        AuditDataMapper.setChangedData(event, info, "description", "venueAddress", "posterUrl");
        String previousPosterUrl = event.getPosterUrl();
        String newPosterUrl = eventPosterService.saveForTransaction(posterImage, previousPosterUrl);

        if (newPosterUrl != null) {
            info.setPosterUrl(newPosterUrl);
        }

        event.update(info);
        eventPolicyOutboxService.enqueue(event, false);

        return event.toResponse();
    }

    /**
     * 공연 정보 삭제
     * @param id
     * @return
     */
    @AuditLog(action = "EVENT_DELETE", targetType = "EVENT")
    public EventResponse delete(Long id) {

        log.info("[DELETE] EventId : {}", id);

        Event event = repository.selectByIdForUpdate(id);
        eventPolicyOutboxService.enqueue(event, true);

        return repository.delete(id).toResponse();
    }

    /**
     * 선택한 공연 ID 목록을 일괄 삭제한다.
     */
    @AuditLog(action = "EVENT_DELETE_BULK", targetType = "EVENT")
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

    /**
     * 공연 회차 정보를 공연 생성 요청으로 변환한다.
     */
    private InsertEventRequest toInsertEventRequest(
            InsertEventCommonRequest common,
            InsertEventScheduleRequest schedule
    ) {

        return InsertEventRequest.builder()
                .artistName(common.getArtistName())
                .title(common.getTitle())
                .eventGroupCode(common.getEventGroupCode())
                .description(common.getDescription())
                .venue(common.getVenue())
                .venueAddress(common.getVenueAddress())
                .posterUrl(common.getPosterUrl())
                .eventDateTime(schedule.getEventDateTime())
                .saleStartAt(schedule.getSaleStartAt())
                .saleEndAt(schedule.getSaleEndAt())
                .queueAdmissionSize(schedule.getQueueAdmissionSize())
                .cancelDeadlineAt(schedule.getCancelDeadlineAt())
                .runningMinutes(common.getRunningMinutes())
                .ageLimit(common.getAgeLimit())
                .totalSeats(common.getTotalSeats())
                .maxTicketsPerPerson(common.getMaxTicketsPerPerson())
                .ticketLimitScope(common.getTicketLimitScope())
                .genre(common.getGenre())
                .region(common.getRegion())
                .theme(common.getTheme())
                .build();
    }
}

