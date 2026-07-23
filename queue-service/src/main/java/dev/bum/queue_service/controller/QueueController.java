package dev.bum.queue_service.controller;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.queue_service.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/events/{eventId}/enter")
    public ResponseEntity<QueueEnterResponse> enter(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(queueService.enter(eventId, currentUserId));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<QueueStatusResponse> status(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(queueService.status(eventId, currentUserId));
    }

    @PostMapping("/validate")
    public ResponseEntity<QueueValidateResponse> validate(@Valid @RequestBody QueueValidateRequest request) {
        return ResponseEntity.ok(queueService.validate(request));
    }
}
