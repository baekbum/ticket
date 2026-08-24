package dev.bum.admin_service.controller.payment;

import dev.bum.admin_service.feign.payment.PaymentRefundProcessServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment/refund-process")
@RequiredArgsConstructor
public class AdminPaymentRefundProcessController {

    private final PaymentRefundProcessServiceClient paymentRefundProcessServiceClient;

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<PaymentRefundProcessResponse>> selectByCond(
            @RequestBody PaymentRefundProcessCondRequest cond
    ) {
        return ResponseEntity.ok(paymentRefundProcessServiceClient.selectByCond(cond));
    }
}
