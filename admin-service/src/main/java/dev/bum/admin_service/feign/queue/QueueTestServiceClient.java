package dev.bum.admin_service.feign.queue;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "queue-test-service", url = "${services.queue-service.url}", path = "/api/v1/manage/queue/test")
public interface QueueTestServiceClient {

    @PostMapping("/events/{eventId}/enter")
    QueueEnterResponse enter(@PathVariable("eventId") Long eventId, @RequestParam("userId") String userId);

    @GetMapping("/events/{eventId}/status")
    QueueStatusResponse status(@PathVariable("eventId") Long eventId, @RequestParam("userId") String userId);

    @PostMapping("/validate")
    QueueValidateResponse validate(@RequestBody QueueValidateRequest request);

    @PostMapping("/events/{eventId}/complete")
    String complete(@PathVariable("eventId") Long eventId,
                    @RequestParam("userId") String userId,
                    @RequestParam("token") String token);

    @DeleteMapping("/events/{eventId}")
    String clearEventQueue(@PathVariable("eventId") Long eventId);
}
