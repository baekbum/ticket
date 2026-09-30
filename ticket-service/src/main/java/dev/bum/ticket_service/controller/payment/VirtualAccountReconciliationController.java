package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.service.payment.VirtualAccountReconciliationResponse;
import dev.bum.ticket_service.service.payment.VirtualAccountReconciliationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manage/payment/virtual-account")
@RequiredArgsConstructor
public class VirtualAccountReconciliationController {

    private final VirtualAccountReconciliationService service;

    @GetMapping
    public ResponseEntity<CustomPageResponse<PaymentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) String paymentNo,
            @RequestParam(required = false) PaymentStatus status
    ) {
        return ResponseEntity.ok(service.list(page, paymentNo, status));
    }

    @GetMapping("/{paymentNo}/reconciliation")
    public ResponseEntity<VirtualAccountReconciliationResponse> inspect(@PathVariable String paymentNo) {
        return ResponseEntity.ok(service.inspect(paymentNo));
    }

    @PostMapping("/{paymentNo}/complete")
    @AuditLog(action = "VIRTUAL_ACCOUNT_ADMIN_RECONCILE", targetType = "PAYMENT")
    public ResponseEntity<PaymentResponse> complete(
            @AuthenticationPrincipal String adminId,
            @PathVariable String paymentNo,
            @Valid @RequestBody CompletionRequest request
    ) {
        return ResponseEntity.ok(service.complete(paymentNo, adminId, request.reason()));
    }

    public record CompletionRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
