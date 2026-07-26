package dev.bum.admin_service.controller.queue;

import dev.bum.admin_service.feign.queue.QueueServiceClient;
import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue/redis")
@RequiredArgsConstructor
public class AdminQueueRedisController {

    private final QueueServiceClient queueServiceClient;

    @GetMapping("/event/{eventId}")
    public ResponseEntity<QueueRedisInspectResponse> inspectEventQueue(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "WAITING") QueueRedisInspectMode mode,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(queueServiceClient.inspectEventQueue(eventId, mode, limit));
    }

    @GetMapping("/token/{token}")
    public ResponseEntity<QueueRedisInspectResponse> inspectToken(@PathVariable("token") String token) {
        return ResponseEntity.ok(queueServiceClient.inspectToken(token));
    }
}
