package dev.bum.payment_gateway_service.controller.refund;

import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.payment_gateway_service.service.refund.GatewayRefundResolutionService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/refunds/internal")
@RequiredArgsConstructor
public class GatewayRefundController {
    private final GatewayRefundResolutionService service;

    @PostMapping("/resolve")
    public GatewayRefundResolutionResponse resolve(@Valid @RequestBody GatewayRefundResolutionRequest request) {
        return service.resolve(request);
    }
}
