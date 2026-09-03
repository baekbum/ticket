package dev.bum.client_api_service.feign.queue;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "client-queue-service", url = "${services.queue-service.url}", path = "/api/v1/queue")
public interface QueueServiceClient {

    @PostMapping("/events/{eventId}/enter")
    QueueEnterResponse enter(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Queue-Force-Enter", defaultValue = "false") boolean force
    );

    @GetMapping("/events/{eventId}/status")
    QueueStatusResponse status(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken
    );

    @PostMapping("/events/{eventId}/leave")
    ResponseEntity<Void> leave(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    );
}
