package dev.bum.client_api_service.controller.queue;

import dev.bum.client_api_service.feign.queue.QueueServiceClient;
import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Queue-Force-Enter", defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(queueServiceClient.enter(eventId, authorizationHeader, force));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<?> status(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken
    ) {
        try {
            return ResponseEntity.ok(queueServiceClient.status(eventId, authorizationHeader, waitingToken));
        } catch (FeignException e) {
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(e.status()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.contentUTF8());
        }
    }

    @PostMapping("/events/{eventId}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        queueServiceClient.leave(eventId, authorizationHeader, waitingToken, activeToken);
        return ResponseEntity.noContent().build();
    }
}
