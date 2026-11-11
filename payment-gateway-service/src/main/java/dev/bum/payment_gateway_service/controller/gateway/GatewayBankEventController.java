package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositCommands;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/financial-simulations/gateway/bank-deposits")
@RequiredArgsConstructor
public class GatewayBankEventController {

    private final GatewayVirtualAccountDepositCommands deposits;

    @PostMapping
    public ResponseEntity<BankDepositAcknowledgement> receive(@Valid @RequestBody BankDepositEvent event) {

        event.validate();
        deposits.confirm(event.deposit().pgPaymentId(), event.deposit());

        return ResponseEntity.ok(BankDepositAcknowledgement.of(event));
    }
}
