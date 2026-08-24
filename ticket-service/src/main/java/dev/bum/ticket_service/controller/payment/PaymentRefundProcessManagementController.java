package dev.bum.ticket_service.controller.payment;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/manage/payment/refund-process")
@RestController
@RequiredArgsConstructor
public class PaymentRefundProcessManagementController {

    private final PaymentRefundProcessService paymentRefundProcessService;

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<PaymentRefundProcessResponse>> selectByCond(
            @RequestBody PaymentRefundProcessCondRequest cond
    ) {
        return ResponseEntity.ok(paymentRefundProcessService.selectByCond(cond));
    }
}
