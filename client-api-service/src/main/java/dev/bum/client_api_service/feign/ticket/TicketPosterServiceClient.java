package dev.bum.client_api_service.feign.ticket;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "client-ticket-poster-service", url = "${services.ticket-service.url}")
public interface TicketPosterServiceClient {

    @GetMapping("/uploads/events/posters/{directory}/{fileName}")
    ResponseEntity<byte[]> selectPoster(
            @PathVariable("directory") String directory,
            @PathVariable("fileName") String fileName
    );
}
