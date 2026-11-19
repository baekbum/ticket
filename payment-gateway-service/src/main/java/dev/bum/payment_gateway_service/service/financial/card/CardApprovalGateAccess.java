package dev.bum.payment_gateway_service.service.financial.card;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalGateRequest;
import dev.bum.payment_gateway_service.jpa.card.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CardApprovalGateAccess {

    private final CardApprovalGateCreator creator;

    private final CardApprovalGateJpaRepository gates;

    private final EntityManager entityManager;

    public CardApprovalGate lock(FinancialCardApprovalGateRequest request) {

        request.validate();

        try {
            creator.createIfAbsent(request);
        } catch (DataIntegrityViolationException race) {
            if (!gates.existsById(request.pgPaymentId())) {
                throw race;
            }
        }

        CardApprovalGate gate = gates.findForUpdate(request.pgPaymentId()).orElseThrow();
        entityManager.refresh(gate, LockModeType.PESSIMISTIC_WRITE);
        gate.validate(request);

        return gate;
    }
}
