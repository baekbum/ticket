package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class BankAccountIssueGateCreator {

    private final BankAccountIssueGateJpaRepository gates;

    private final EntityManager entityManager;

    // 미존재 거래의 잠금 행만 별도 커밋한다. 발급 실패에도 차단 행은 사라지지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(BankVirtualAccountIssueRequest request) {

        if (!gates.existsById(request.pgPaymentId())) {
            // merge/save로 기존 CLOSED 행을 OPEN 값으로 덮어쓰지 않도록 INSERT만 실행한다.
            entityManager.persist(BankAccountIssueGate.open(request));
            entityManager.flush();
        }
    }
}
