package dev.bum.ticket_service.controller.area;

import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.service.area.AreaService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.queue.QueueAccessService.ActiveSession;
import dev.bum.ticket_service.service.seat.SeatActivityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RequestMapping("/api/v1/area")
@RestController
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;
    private final QueueAccessService queueAccessService;
    private final SeatActivityTracker seatActivityTracker;
    private final TicketingDetectionLogProducer detectionLogProducer;

    @GetMapping("/layout/event/{eventId}")
    public ResponseEntity<EventLayoutResponse> selectLayout(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {

        ActiveSession session;

        try {
            session = queueAccessService.validate(eventId, currentUserId, activeToken);
        } catch (QueueAccessDeniedException e) {
            // Active Token 없이 해당 API에 접근했을 경우
            if (StringUtils.hasText(currentUserId) && !StringUtils.hasText(activeToken)) {
                detectionLogProducer.report(eventId, currentUserId, null,
                        TicketingDetectionType.AREA_LAYOUT_ACTIVE_TOKEN_MISSING, Instant.now(), Map.of());
            }
            throw e;
        }

        seatActivityTracker.recordAreaLayoutQuery(eventId, currentUserId, activeToken, session);
        EventLayoutResponse response = areaService.selectLayout(eventId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @GetMapping("/select/id/{areaId}")
    public ResponseEntity<AreaResponse> selectById(@PathVariable("areaId") Long areaId) {

        return ResponseEntity.ok(areaService.selectById(areaId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<AreaResponse>> selectByCond(@ModelAttribute AreaCondRequest cond) {

        return ResponseEntity.ok(areaService.selectByCond(cond));
    }
}
