package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class BankVirtualAccountIssuanceService {

    // 발급 트랜잭션과 유니크 충돌 후 재조회를 별도 프록시에서 수행한다.
    private final BankVirtualAccountIssueCommandService commands;

    /**
     * 동시 요청도 한 계좌로 수렴한다. 실패한 트랜잭션을 재사용하지 않는다.
     */
    public BankVirtualAccountIssueResponse issue(BankVirtualAccountIssueRequest request) {

        try {
            return commands.issue(request);
        } catch (DataIntegrityViolationException conflict) {
            return commands.replayCommitted(request).orElseThrow(() -> conflict);
        }
    }
}
