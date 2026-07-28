package dev.bum.admin_service.controller.audit;

import dev.bum.admin_service.feign.audit.AuditServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.AuditLogCondRequest;
import dev.bum.common.service.audit.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditServiceClient auditServiceClient;

    @GetMapping("/select/id/{id}")
    public ResponseEntity<AuditLogResponse> selectById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(auditServiceClient.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<AuditLogResponse>> selectByCond(@RequestBody(required = false) AuditLogCondRequest cond) {
        return ResponseEntity.ok(auditServiceClient.selectByCond(cond));
    }
}
