package dev.bum.ticket_service.feign.queue;

import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "queue-service-client", url = "${app.queue.base-url}")
public interface QueueServiceClient {

    @PostMapping("/api/v1/queue/validate")
    QueueValidateResponse validate(@RequestBody QueueValidateRequest request);
}
