package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GatewayVirtualAccountIssueCommandService {

    // 인증한 자격증명과 현재 가맹점 등록·상태를 확인한다.
    private final MerchantJpaRepository merchants;
    // PG 행 잠금은 짧은 검증/확정 트랜잭션 안에서만 획득한다.
    private final GatewayPaymentJpaRepository payments;
    // 성공한 최초 발급 결과를 금융 HTTP 재호출 없이 재응답한다.
    private final GatewayPaymentHistoryJpaRepository histories;

    /**
     * 인증·원거래를 확인하고 HTTP 호출용 불변 값 또는 저장된 최초 결과를 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayVirtualAccountIssuePreparation inspect(
            AuthenticatedMerchant principal,
            String pgPaymentId,
            GatewayVirtualAccountPaymentIssueRequest request
    ) {

        Merchant merchant = authenticatedMerchant(principal, request);
        GatewayPayment payment = ownedPayment(principal, pgPaymentId);
        validateRequest(payment, request);

        BankVirtualAccountIssueRequest bankRequest = new BankVirtualAccountIssueRequest(payment.getPgPaymentId(),
                payment.getBankCompany(), payment.getAmount(), payment.getExpiresAt());
        Optional<GatewayPaymentHistory> existing = issuedHistory(pgPaymentId);

        if (existing.isPresent()) {
            return new GatewayVirtualAccountIssuePreparation(bankRequest, replay(payment, existing.get()),
                    merchant.getStatus() == MerchantStatus.ACTIVE);
        }

        if (payment.getStatus() != GatewayPaymentStatus.READY) {
            throw new GatewayPaymentConflictException("새 계좌를 발급할 수 없는 PG 거래 상태입니다.");
        }

        return new GatewayVirtualAccountIssuePreparation(bankRequest, null, merchant.getStatus() == MerchantStatus.ACTIVE);
    }

    /**
     * 실제 금융 사실을 검증하고 PG 상태와 최초 발급 이력을 같은 트랜잭션으로 확정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayVirtualAccountPaymentIssueResponse confirm(
            AuthenticatedMerchant principal,
            String pgPaymentId,
            GatewayVirtualAccountPaymentIssueRequest request,
            BankVirtualAccountIssueResponse bank
    ) {

        authenticatedMerchant(principal, request);
        GatewayPayment payment = ownedPayment(principal, pgPaymentId);
        validateRequest(payment, request);
        validateBankResponse(payment, bank);
        Optional<GatewayPaymentHistory> existing = issuedHistory(pgPaymentId);

        if (existing.isPresent()) {
            GatewayVirtualAccountPaymentIssueResponse previous = replay(payment, existing.get());

            if (!previous.accountNumber().equals(bank.accountNumber())) {
                throw new FinancialProviderResponseException();
            }

            return previous;
        }

        if (payment.getStatus() != GatewayPaymentStatus.READY) {
            throw new GatewayPaymentConflictException("PG 거래 상태가 바뀌어 발급 결과를 반영할 수 없습니다.");
        }

        GatewayPaymentStatus beforeStatus = payment.getStatus();
        payment.waitForDeposit();
        histories.saveAndFlush(GatewayPaymentHistory.accountIssued(payment, bank.accountNumber(), bank.bankName(),
                bank.status().name(), beforeStatus));

        return GatewayVirtualAccountPaymentIssueResponse.from(payment, bank);
    }

    /**
     * 요청의 가맹점 코드는 인증된 코드와 일치하는지만 확인한다.
     */
    private Merchant authenticatedMerchant(
            AuthenticatedMerchant principal,
            GatewayVirtualAccountPaymentIssueRequest request
    ) {

        if (principal == null || principal.merchantId() == null || principal.credentialKey() == null || request == null) {
            throw new AccessDeniedException("가맹점 인증 정보가 필요합니다.");
        }

        if (request.merchantId() != null && !request.merchantId().equals(principal.merchantId())) {
            throw new AccessDeniedException("인증된 가맹점과 요청 가맹점이 일치하지 않습니다.");
        }

        return merchants.findById(principal.merchantId())
                .filter(found -> found.getCredentialKey().equals(principal.credentialKey()))
                .orElseThrow(() -> new AccessDeniedException("가맹점 인증 정보가 변경되었습니다."));
    }

    /**
     * 다른 가맹점의 PG ID와 존재하지 않는 ID를 동일한 404로 처리한다.
     */
    private GatewayPayment ownedPayment(AuthenticatedMerchant principal, String pgPaymentId) {

        return payments.findOwnedForUpdate(principal.merchantId(), pgPaymentId)
                .orElseThrow(GatewayPaymentNotFoundException::new);
    }

    /**
     * 요청 수단으로 분기하지 않고 저장된 수단·소유자·은행·금액과 대조한다.
     */
    private void validateRequest(GatewayPayment payment, GatewayVirtualAccountPaymentIssueRequest request) {

        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || !payment.getBuyerReference().equals(request.buyerReference())
                || request.amount() == null || payment.getAmount().compareTo(request.amount()) != 0
                || payment.getBankCompany() != request.bankCompany()) {
            throw new GatewayPaymentConflictException("계좌 발급 요청이 준비된 PG 원거래와 일치하지 않습니다.");
        }
    }

    /**
     * 은행의 다른 거래 응답이나 변조된 결과를 PG 금융 성공으로 반영하지 않는다.
     */
    private void validateBankResponse(GatewayPayment payment, BankVirtualAccountIssueResponse bank) {

        if (bank == null || !payment.getPgPaymentId().equals(bank.pgPaymentId())
                || payment.getBankCompany() != bank.bankCompany()
                || !payment.getBankCompany().getBankName().equals(bank.bankName())
                || bank.amount() == null || payment.getAmount().compareTo(bank.amount()) != 0
                || !payment.getExpiresAt().equals(bank.expiresAt())
                || bank.status() != VirtualAccountPaymentStatus.WAITING_DEPOSIT
                || bank.accountNumber() == null || bank.accountNumber().isBlank() || bank.accountNumber().length() > 30) {
            throw new FinancialProviderResponseException();
        }
    }

    /**
     * 같은 거래·작업·키를 조회한다. 재시도 때 새로운 작업 키를 만들지 않는다.
     */
    private Optional<GatewayPaymentHistory> issuedHistory(String pgPaymentId) {

        return histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                pgPaymentId, GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL");
    }

    /**
     * 최초 성공 스냅샷으로 같은 계좌를 반환한다. 통보 상태나 은행 상태를 새로 추정하지 않는다.
     */
    private GatewayVirtualAccountPaymentIssueResponse replay(GatewayPayment payment, GatewayPaymentHistory history) {

        Object number = history.getDetails().get("accountNumber");
        Object name = history.getDetails().get("bankName");
        Object status = history.getDetails().get("bankStatus");

        if (!(number instanceof String accountNumber) || accountNumber.isBlank()
                || !(name instanceof String bankName) || bankName.isBlank()
                || !VirtualAccountPaymentStatus.WAITING_DEPOSIT.name().equals(status)) {
            throw new FinancialProviderResponseException();
        }

        BankVirtualAccountIssueResponse bank = new BankVirtualAccountIssueResponse(payment.getPgPaymentId(),
                payment.getBankCompany(), bankName, accountNumber, payment.getAmount(), payment.getExpiresAt(),
                VirtualAccountPaymentStatus.WAITING_DEPOSIT);

        return GatewayVirtualAccountPaymentIssueResponse.from(payment, bank);
    }
}
