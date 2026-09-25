package dev.bum.audit_service.login;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.LoginLogCondRequest;
import dev.bum.common.service.audit.dto.LoginLogResponse;
import dev.bum.common.service.audit.dto.MyLoginLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/login-log")
public class LoginLogController {

    private final LoginLogPersistenceService loginLogPersistenceService;

    @GetMapping("/select/id/{id}")
    public ResponseEntity<LoginLogResponse> selectById(@PathVariable Long id) {
        return ResponseEntity.ok(loginLogPersistenceService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<LoginLogResponse>> selectByCond(
            @RequestBody(required = false) LoginLogCondRequest cond
    ) {
        return ResponseEntity.ok(loginLogPersistenceService.selectByCond(cond));
    }

    @GetMapping("/me")
    public ResponseEntity<CustomPageResponse<MyLoginLogResponse>> selectMine(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestParam(defaultValue = "30") Integer periodDays
    ) {
        return ResponseEntity.ok(loginLogPersistenceService.selectMine(currentUserId, page, size, periodDays));
    }
}
