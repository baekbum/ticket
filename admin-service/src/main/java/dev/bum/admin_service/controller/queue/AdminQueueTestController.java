package dev.bum.admin_service.controller.queue;

import dev.bum.admin_service.feign.queue.QueueTestServiceClient;
import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue/test")
@RequiredArgsConstructor
public class AdminQueueTestController {

    private final QueueTestServiceClient queueTestServiceClient;

    @PostMapping("/events/{eventId}/enter")
    public ResponseEntity<QueueEnterResponse> enter(
            @PathVariable("eventId") Long eventId,
            @RequestParam("userId") String userId
    ) {
        return ResponseEntity.ok(queueTestServiceClient.enter(eventId, userId));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<QueueStatusResponse> status(
            @PathVariable("eventId") Long eventId,
            @RequestParam("userId") String userId
    ) {
        return ResponseEntity.ok(queueTestServiceClient.status(eventId, userId));
    }

    @PostMapping("/validate")
    public ResponseEntity<QueueValidateResponse> validate(@RequestBody QueueValidateRequest request) {
        return ResponseEntity.ok(queueTestServiceClient.validate(request));
    }

    @PostMapping("/events/{eventId}/complete")
    public ResponseEntity<String> complete(
            @PathVariable("eventId") Long eventId,
            @RequestParam("userId") String userId,
            @RequestParam("token") String token
    ) {
        return ResponseEntity.ok(queueTestServiceClient.complete(eventId, userId, token));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<String> clearEventQueue(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(queueTestServiceClient.clearEventQueue(eventId));
    }
}
