package dev.bum.queue_service.controller;

import dev.bum.common.error.ErrorCode;
import dev.bum.common.error.ErrorResponse;
import dev.bum.common.service.queue.dto.*;
import dev.bum.queue_service.service.reactive.ReactiveQueueAdminService;
import dev.bum.queue_service.service.reactive.ReactiveQueueService;
import dev.bum.queue_service.service.reactive.ReactiveQueueTestManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manage/queue/test")
@RequiredArgsConstructor
public class QueueTestManagementController {
    private final ReactiveQueueService queueService;
    private final ReactiveQueueAdminService queueAdminService;
    private final ReactiveQueueTestManagementService queueTestManagementService;

    @PostMapping("/events/{eventId}/enter")
    public Mono<ResponseEntity<QueueEnterResponse>> enter(
            @PathVariable Long eventId, @RequestParam String userId,
            @RequestParam(defaultValue = "false") boolean force) {
        return queueService.enter(eventId, userId, force).map(ResponseEntity::ok);
    }

    @GetMapping("/events/{eventId}/status")
    public Mono<ResponseEntity<QueueStatusResponse>> status(
            @PathVariable Long eventId, @RequestParam String userId,
            @RequestParam(required = false) String token) {
        return queueService.status(eventId, userId, token).map(ResponseEntity::ok);
    }

    @PostMapping("/events/{eventId}/statuses")
    public Mono<ResponseEntity<List<QueueStatusResponse>>> statuses(
            @PathVariable Long eventId, @RequestBody QueueBulkStatusRequest request) {
        return queueAdminService.statuses(eventId, request.userIds(), request.tokenByUserId())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<QueueValidateResponse>> validate(@RequestBody QueueValidateRequest request) {
        return queueService.validate(request).map(ResponseEntity::ok);
    }

    @PostMapping("/events/{eventId}/complete")
    public Mono<ResponseEntity<?>> complete(
            @PathVariable Long eventId, @RequestParam String userId, @RequestParam String token) {
        return queueService.complete(eventId, userId, token).map(completed -> completed
                ? ResponseEntity.ok("대기열 active 토큰을 완료 처리했습니다.")
                : ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.QUEUE_TOKEN_INVALID)));
    }

    @DeleteMapping("/events/{eventId}")
    public Mono<ResponseEntity<String>> clearEventQueue(@PathVariable Long eventId) {
        return queueTestManagementService.clearEventQueue(eventId).map(deleted ->
                ResponseEntity.ok(String.format("이벤트 %d번 대기열 Redis key %d건을 삭제했습니다.", eventId, deleted)));
    }
}
