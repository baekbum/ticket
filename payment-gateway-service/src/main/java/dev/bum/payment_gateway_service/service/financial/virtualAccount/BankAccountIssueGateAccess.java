package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BankAccountIssueGateAccess {

    private final BankAccountIssueGateCreator creator;

    private final BankAccountIssueGateJpaRepository gates;

    private final EntityManager entityManager;

    public BankAccountIssueGate lock(BankVirtualAccountIssueRequest request) {

        try {
            creator.createIfAbsent(request);
        } catch (DataIntegrityViolationException race) {
            if (!gates.existsById(request.pgPaymentId())) {
                throw race;
            }
        }

        BankAccountIssueGate gate = gates.findForUpdate(request.pgPaymentId()).orElseThrow();
        entityManager.refresh(gate, LockModeType.PESSIMISTIC_WRITE);
        gate.validate(request);

        return gate;
    }
}
