package dev.bum.queue_service.controller;

import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import dev.bum.queue_service.service.QueueRedisInspectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manage/queue/redis")
@RequiredArgsConstructor
public class QueueRedisManagementController {

    private final QueueRedisInspectService queueRedisInspectService;

    @GetMapping("/event/{eventId}")
    public ResponseEntity<QueueRedisInspectResponse> inspectEventQueue(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "WAITING") QueueRedisInspectMode mode,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(queueRedisInspectService.inspectEventQueue(eventId, mode, limit));
    }

    @GetMapping("/token/{token}")
    public ResponseEntity<QueueRedisInspectResponse> inspectToken(@PathVariable("token") String token) {
        return ResponseEntity.ok(queueRedisInspectService.inspectToken(token));
    }
}
