package dev.bum.payment_gateway_service.service.financial.card;

import dev.bum.payment_gateway_service.dto.card.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class CardApprovalService {

    // 최초 승인과 유니크 충돌 복구를 다른 금융 트랜잭션으로 분리한다.
    private final CardApprovalCommandService commands;

    /**
     * 같은 PG 식별자의 동시 승인도 하나의 원승인으로 수렴시킨다.
     */
    public FinancialCardApprovalResponse approve(FinancialCardApproveRequest request) {

        try {
            return commands.approve(request);
        } catch (DataIntegrityViolationException conflict) {
            return commands.replayCommitted(request).orElseThrow(() -> conflict);
        }
    }

    /**
     * PG 식별자로 원승인 사실을 조회한다.
     */
    public Optional<FinancialCardApprovalResponse> find(String pgPaymentId) {

        return commands.find(pgPaymentId);
    }

    /**
     * 확정 거절된 원승인을 멱등 보상한다.
     */
    public FinancialCardApprovalResponse cancel(String pgPaymentId) {

        return commands.cancel(pgPaymentId);
    }

}
