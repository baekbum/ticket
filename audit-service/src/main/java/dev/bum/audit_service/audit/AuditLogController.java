package dev.bum.audit_service.audit;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.AuditLogCondRequest;
import dev.bum.common.service.audit.dto.AuditLogResponse;
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
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    private final AuditLogPersistenceService auditLogPersistenceService;

    @GetMapping("/select/id/{id}")
    public ResponseEntity<AuditLogResponse> selectById(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogPersistenceService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<AuditLogResponse>> selectByCond(@RequestBody(required = false) AuditLogCondRequest cond) {
        return ResponseEntity.ok(auditLogPersistenceService.selectByCond(cond));
    }
}
