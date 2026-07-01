package dev.bum.admin_service.controller.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.event.EventServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventServiceClient eventServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * 이벤트 등록 기능
     * @param info
     * @return
     */
    @PostMapping(value = "/insert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> insert(
            @Valid @RequestPart("event") InsertEventRequest info,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventServiceClient.insert(toJson(info), posterImage));
    }

    /**
     * ID로 이벤트 정보를 검색하는 기능
     * @param eventId
     * @return
     */
    @GetMapping("/select/id/{eventId}")
    public ResponseEntity<EventResponse> selectById(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventServiceClient.selectById(eventId));
    }

    /**
     * 조건에 따라 이벤트를 검색하는 기능
     * @param cond
     * @return
     */
    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<EventResponse>> selectByCond(@RequestBody EventCondRequest cond) {
        return ResponseEntity.ok(eventServiceClient.selectByCond(cond));
    }

    /**
     * 이벤트 정보를 업데이트 하는 기능
     * @param eventId
     * @param info
     * @return
     */
    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventResponse> update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest info) {
        return ResponseEntity.ok(eventServiceClient.update(eventId, info));
    }

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> update(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestPart("event") UpdateEventRequest info,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventServiceClient.update(eventId, toJson(info), posterImage));
    }

    /**
     * 이벤트를 삭제하는 기능
     * @param eventId
     * @return
     */
    @DeleteMapping("/delete/id/{eventId}")
    public ResponseEntity<EventResponse> delete(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventServiceClient.delete(eventId));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event payload.", e);
        }
    }
}
