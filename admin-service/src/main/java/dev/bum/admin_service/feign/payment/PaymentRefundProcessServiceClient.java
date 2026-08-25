package dev.bum.admin_service.feign.payment;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessCondRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-refund-process-service", url = "${services.ticket-service.url}", path = "/api/v1/manage/payment/refund-process")
public interface PaymentRefundProcessServiceClient {

    @PostMapping("/select")
    CustomPageResponse<PaymentRefundProcessResponse> selectByCond(@RequestBody PaymentRefundProcessCondRequest cond);

    @PutMapping("/local-complete/id/{paymentRefundProcessId}")
    PaymentRefundProcessResponse completeLocal(@PathVariable("paymentRefundProcessId") Long paymentRefundProcessId);
}
