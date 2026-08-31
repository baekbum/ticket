package dev.bum.client_api_service.controller.queue;

import dev.bum.client_api_service.feign.queue.QueueServiceClient;
import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class ClientQueueController {

    private final QueueServiceClient queueServiceClient;

    @PostMapping("/events/{eventId}/enter")
    public ResponseEntity<QueueEnterResponse> enter(
            @PathVariable("eventId") Long eventId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        return ResponseEntity.ok(queueServiceClient.enter(eventId, activeToken));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<QueueStatusResponse> status(
            @PathVariable("eventId") Long eventId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        return ResponseEntity.ok(queueServiceClient.status(eventId, activeToken));
    }

    @PostMapping("/events/{eventId}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable("eventId") Long eventId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        queueServiceClient.leave(eventId, activeToken);
        return ResponseEntity.noContent().build();
    }
}
