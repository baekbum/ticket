package dev.bum.audit_service.ticketing;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.TicketingDetectionLogCondRequest;
import dev.bum.common.service.audit.dto.TicketingDetectionLogDetailResponse;
import dev.bum.common.service.audit.dto.TicketingDetectionLogListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ticketing-detection-log")
public class TicketingDetectionLogController {

    private final TicketingDetectionLogPersistenceService persistenceService;

    @GetMapping("/select/id/{id}")
    public ResponseEntity<TicketingDetectionLogDetailResponse> selectById(@PathVariable Long id) {
        return ResponseEntity.ok(persistenceService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<TicketingDetectionLogListResponse>> selectByCond(
            @RequestBody(required = false) TicketingDetectionLogCondRequest cond
    ) {
        return ResponseEntity.ok(persistenceService.selectByCond(cond));
    }
}
