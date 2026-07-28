package dev.bum.admin_service.feign.audit;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.AuditLogCondRequest;
import dev.bum.common.service.audit.dto.AuditLogResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "audit-service", url = "${services.audit-service.url}", path = "/api/v1/audit-log")
public interface AuditServiceClient {

    @GetMapping("/select/id/{id}")
    AuditLogResponse selectById(@PathVariable("id") Long id);

    @PostMapping("/select")
    CustomPageResponse<AuditLogResponse> selectByCond(@RequestBody AuditLogCondRequest cond);
}
