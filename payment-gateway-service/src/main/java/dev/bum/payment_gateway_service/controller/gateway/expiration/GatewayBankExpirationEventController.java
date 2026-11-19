package dev.bum.payment_gateway_service.controller.gateway.expiration;

import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountExpirationCommands;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/financial-simulations/gateway/bank-expirations")
@RequiredArgsConstructor
public class GatewayBankExpirationEventController {

    private final GatewayVirtualAccountExpirationCommands expirations;

    @PostMapping
    public ResponseEntity<BankExpirationAcknowledgement> receive(@Valid @RequestBody BankExpirationEvent event) {

        expirations.confirm(event);

        return ResponseEntity.ok(BankExpirationAcknowledgement.of(event));
    }
}
