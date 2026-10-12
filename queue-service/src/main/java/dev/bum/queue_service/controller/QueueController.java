package dev.bum.queue_service.controller;

import dev.bum.common.service.queue.dto.*;
import dev.bum.queue_service.service.reactive.ReactiveQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {
    private final ReactiveQueueService queueService;

    @PostMapping("/events/{eventId}/enter")
    public Mono<ResponseEntity<QueueEnterResponse>> enter(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Queue-Force-Enter", defaultValue = "false") boolean force) {
        return queueService.enter(eventId, currentUserId, force)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/events/{eventId}/status")
    public Mono<ResponseEntity<QueueStatusResponse>> status(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken) {
        return queueService.status(eventId, currentUserId, waitingToken)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/events/{eventId}/leave")
    public Mono<ResponseEntity<Void>> leave(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken) {
        return queueService.leave(eventId, currentUserId, waitingToken, activeToken)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<QueueValidateResponse>> validate(@Valid @RequestBody QueueValidateRequest request) {
        return queueService.validate(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/complete")
    public Mono<ResponseEntity<Void>> complete(@Valid @RequestBody QueueCompleteRequest request) {
        return queueService.complete(request.eventId(), request.userId(), request.token())
                .map(completed -> completed
                        ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build());
    }
}
