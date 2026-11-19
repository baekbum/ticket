package dev.bum.payment_gateway_service.service.financial.card;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.support.CardCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardApprovalCommandService {

    // 같은 원장을 잠가 서로 다른 거래도 한도를 초과하거나 누적액을 덮어쓰지 못하게 한다.
    private final DummyCardJpaRepository cards;
    // PG 식별자별 원승인은 한 번만 저장한다.
    private final DummyCardPaymentHistoryJpaRepository approvals;
    // 기존 카드 인증과 같은 BCrypt 검증기를 사용한다.
    private final PasswordEncoder encoder;

    // 승인 이력이 생기기 전부터 승인·수단 전환을 같은 금융 잠금으로 직렬화한다.
    private final CardApprovalGateAccess gates;

    /**
     * Ticksy 호출 없이 카드 인증·한도 반영·원승인을 한 금융 트랜잭션으로 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinancialCardApprovalResponse approve(FinancialCardApproveRequest request) {

        CardApprovalGate gate = gates.lock(new FinancialCardApprovalGateRequest(request.pgPaymentId(),
                request.buyerReference(), request.amount(), request.expiresAt()));

        Optional<DummyCardPaymentHistory> existing = approvals.findByPgPaymentId(request.pgPaymentId());

        if (gate.isClosed() && (existing.isEmpty() || existing.get().getStatus() == CardPaymentHistoryStatus.APPROVAL_FAILED)) {
            throw new GatewayPaymentConflictException("결제수단 전환으로 카드 승인이 차단된 거래입니다.");
        }

        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        CardCredentials.identity(request.cardCompany(), request.cardNumber());
        String number = CardCredentials.normalize(request.cardNumber());
        DummyCard card = cards.findByCardCompanyAndCardNumberHash(request.cardCompany(), CardCredentials.hash(number))
                .orElseThrow(() -> new IllegalArgumentException("카드 정보가 일치하지 않습니다."));
        existing = approvals.findByPgPaymentId(request.pgPaymentId());

        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        if (!request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("카드 승인 기한이 만료되었습니다.");
        }

        CardCredentials.validate(card, number, request.cvc(), request.cardPassword(), encoder);
        card.approve(request.amount());
        String transactionId = "CARD-" + LocalDate.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.BASIC_ISO_DATE) + UUID.randomUUID();
        DummyCardPaymentHistory history = approvals.saveAndFlush(DummyCardPaymentHistory.approved(request.pgPaymentId(),
                card, request.buyerReference(), request.pgPaymentId(), transactionId,
                CardCredentials.mask(number), request.amount()));

        return FinancialCardApprovalResponse.from(history);
    }

    // 승인보다 먼저 커밋되면 이후 승인도 막는다. 승인이 먼저면 그 사실을 반환한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinancialCardApprovalGateResponse close(FinancialCardApprovalGateRequest request) {

        CardApprovalGate gate = gates.lock(request);
        Optional<DummyCardPaymentHistory> existing = approvals.findByPgPaymentId(request.pgPaymentId());

        if (existing.isPresent()) {
            DummyCardPaymentHistory history = existing.get();

            if (!history.getUserId().equals(request.buyerReference()) || history.getAmount().compareTo(request.amount()) != 0) {
                throw new GatewayPaymentConflictException("카드 원승인의 구매자 또는 금액이 일치하지 않습니다.");
            }

            if (history.getStatus() != CardPaymentHistoryStatus.APPROVAL_FAILED) {
                return new FinancialCardApprovalGateResponse(request, false, FinancialCardApprovalResponse.from(history));
            }
        }

        gate.close();

        return new FinancialCardApprovalGateResponse(request, true, null);
    }

    /**
     * 충돌한 승인 트랜잭션이 롤백된 뒤 먼저 커밋된 동일 원승인만 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<FinancialCardApprovalResponse> replayCommitted(FinancialCardApproveRequest request) {

        return approvals.findByPgPaymentId(request.pgPaymentId()).map(history -> replay(history, request));
    }

    /**
     * 응답 유실 뒤 기존 금융 승인 사실을 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialCardApprovalResponse> find(String pgPaymentId) {

        return approvals.findByPgPaymentId(pgPaymentId).map(FinancialCardApprovalResponse::from);
    }

    /**
     * 가맹점의 확정 거절에 대한 승인 보상을 멱등 처리한다. 한도 복원과 취소 상태는 함께 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinancialCardApprovalResponse cancel(String pgPaymentId) {

        DummyCardPaymentHistory history = approvals.findByPgPaymentIdForUpdate(pgPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("카드 승인 이력을 찾을 수 없습니다."));

        if (history.getStatus() == CardPaymentHistoryStatus.CANCELLED) {
            return FinancialCardApprovalResponse.from(history);
        }

        if (history.getStatus() != CardPaymentHistoryStatus.APPROVED) {
            throw new GatewayPaymentConflictException("승인 보상을 처리할 수 없는 카드 상태입니다.");
        }

        DummyCard card = cards.findByIdForUpdate(history.getDummyCard().getDummyCardId()).orElseThrow();
        card.cancelApproval(history.getAmount());
        history.cancel("MERCHANT_PAYMENT_REJECTED");

        return FinancialCardApprovalResponse.from(history);
    }

    /**
     * 원승인 이후에는 같은 금액·예매자·카드만 허용하고 다시 승인하거나 비밀값을 저장하지 않는다.
     */
    private FinancialCardApprovalResponse replay(DummyCardPaymentHistory history, FinancialCardApproveRequest request) {

        FinancialCardApprovalResponse result = FinancialCardApprovalResponse.from(history);

        if (!history.getUserId().equals(request.buyerReference())
                || history.getAmount().compareTo(request.amount()) != 0
                || !result.cardIdentity().equals(CardCredentials.identity(request.cardCompany(), request.cardNumber()))) {
            throw new GatewayPaymentConflictException("동일 PG 거래의 승인 요청이 일치하지 않습니다.");
        }

        return result;
    }
}
