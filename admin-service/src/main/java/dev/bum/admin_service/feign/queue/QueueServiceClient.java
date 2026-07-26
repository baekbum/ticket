package dev.bum.admin_service.feign.queue;

import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "queue-service", url = "${services.queue-service.url}", path = "/api/v1/manage/queue/redis")
public interface QueueServiceClient {

    @GetMapping("/event/{eventId}")
    QueueRedisInspectResponse inspectEventQueue(
            @PathVariable("eventId") Long eventId,
            @RequestParam("mode") QueueRedisInspectMode mode,
            @RequestParam("limit") int limit
    );

    @GetMapping("/token/{token}")
    QueueRedisInspectResponse inspectToken(@PathVariable("token") String token);
}
