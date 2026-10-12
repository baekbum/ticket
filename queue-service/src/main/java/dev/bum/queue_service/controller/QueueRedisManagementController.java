package dev.bum.queue_service.controller;

import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import dev.bum.queue_service.service.reactive.ReactiveQueueRedisInspectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/manage/queue/redis")
@RequiredArgsConstructor
public class QueueRedisManagementController {
    private final ReactiveQueueRedisInspectService queueRedisInspectService;

    @GetMapping("/event/{eventId}")
    public Mono<ResponseEntity<QueueRedisInspectResponse>> inspectEventQueue(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "WAITING") QueueRedisInspectMode mode,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return queueRedisInspectService.inspectEventQueue(eventId, mode, limit)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/token/{token}")
    public Mono<ResponseEntity<QueueRedisInspectResponse>> inspectToken(@PathVariable("token") String token) {
        return queueRedisInspectService.inspectToken(token)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/event/{eventId}/token/{token}")
    public Mono<ResponseEntity<Void>> removeToken(
            @PathVariable("eventId") Long eventId,
            @PathVariable("token") String token,
            @RequestParam("mode") QueueRedisInspectMode mode) {
        return queueRedisInspectService.removeToken(eventId, token, mode)
                .map(removed -> removed
                        ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
