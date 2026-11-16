package dev.bum.payment_gateway_service.service.financial.card;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalGateRequest;
import dev.bum.payment_gateway_service.jpa.card.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class CardApprovalGateCreator {

    private final CardApprovalGateJpaRepository gates;

    private final EntityManager entityManager;

    // 미존재 거래의 잠금 행만 별도 커밋한다. 승인 실패에도 차단 행은 사라지지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(FinancialCardApprovalGateRequest request) {

        if (!gates.existsById(request.pgPaymentId())) {
            // merge/save로 기존 CLOSED 행을 OPEN 값으로 덮어쓰지 않도록 INSERT만 실행한다.
            entityManager.persist(CardApprovalGate.open(request));
            entityManager.flush();
        }
    }
}
