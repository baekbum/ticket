package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;

import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class BankVirtualAccountIssueCommandService {

    private final BankAccountIssueGateAccess gates;

    // PG 식별자별 한 계좌를 저장하며 계좌번호는 기존 계좌와도 전역 중복을 허용하지 않는다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 계좌와 발급 이력을 같은 금융 트랜잭션으로 저장한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;

    /**
     * 은행 책임의 번호 발급·계좌 저장·이력만 처리한다. Ticksy 업무 규칙과 API는 참조하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankVirtualAccountIssueResponse issue(BankVirtualAccountIssueRequest request) {

        BankAccountIssueGate gate = gates.lock(request);

        if (gate.isClosed()) {
            throw new GatewayPaymentConflictException("결제수단 변경으로 계좌 발급이 차단된 거래입니다.");
        }

        Optional<DummyVirtualAccount> existing = accounts.findByPgPaymentId(request.pgPaymentId());

        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        if (!request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("입금 기한이 만료되어 계좌를 발급할 수 없습니다.");
        }

        String accountNumber = generateAccountNumber(request);
        DummyVirtualAccount account = accounts.saveAndFlush(DummyVirtualAccount.issue(request.pgPaymentId(),
                request.pgPaymentId(), request.bankCompany(), accountNumber, request.amount(), request.expiresAt()));
        histories.saveAndFlush(DummyVirtualAccountPaymentHistory.issued(account));

        return BankVirtualAccountIssueResponse.from(account);
    }

    // 계좌의 현재 상태와 무관하게 최초 발급 사실이 있으면 차단·전환을 거절한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankAccountIssueGateResponse closeIssue(BankVirtualAccountIssueRequest request) {

        BankAccountIssueGate gate = gates.lock(request);
        Optional<DummyVirtualAccount> existing = accounts.findByPgPaymentId(request.pgPaymentId());

        if (existing.isPresent()) {
            return new BankAccountIssueGateResponse(request, false, replay(existing.get(), request));
        }

        gate.close();

        return new BankAccountIssueGateResponse(request, true, null);
    }

    /**
     * 동시 최초 발급의 유니크 충돌이 롤백된 후 먼저 커밋된 발급 결과를 재조회한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<BankVirtualAccountIssueResponse> replayCommitted(BankVirtualAccountIssueRequest request) {

        return accounts.findByPgPaymentId(request.pgPaymentId()).map(account -> replay(account, request));
    }

    /**
     * HTTP 응답 유실을 복구할 때 PG 거래의 은행 결과만 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<BankVirtualAccountIssueResponse> find(String pgPaymentId) {

        return accounts.findByPgPaymentId(pgPaymentId).map(BankVirtualAccountIssueResponse::from);
    }

    /**
     * 같은 PG 식별자로 다른 은행·금액·기한을 발급하는 요청을 거절한다.
     */
    private BankVirtualAccountIssueResponse replay(
            DummyVirtualAccount account,
            BankVirtualAccountIssueRequest request
    ) {

        if (account.getBankCompany() != request.bankCompany()
                || account.getAmount().compareTo(request.amount()) != 0
                || !account.getExpiresAt().equals(request.expiresAt())) {
            throw new IllegalArgumentException("같은 PG 거래의 계좌 발급 정보가 일치하지 않습니다.");
        }

        return BankVirtualAccountIssueResponse.from(account);
    }

    /**
     * 은행별 번호 형식을 유지하고 기존 계좌를 포함해 중복 여부를 확인한다.
     */
    private String generateAccountNumber(BankVirtualAccountIssueRequest request) {

        for (int attempt = 0; attempt < 5; attempt++) {
            String number = request.bankCompany().getAccountPrefix() + "-"
                    + ThreadLocalRandom.current().nextInt(1000, 10000) + "-"
                    + ThreadLocalRandom.current().nextInt(100000, 1000000);

            if (!accounts.existsByAccountNumber(number)) {
                return number;
            }
        }

        throw new IllegalStateException("가상계좌 번호를 발급하지 못했습니다.");
    }
}
