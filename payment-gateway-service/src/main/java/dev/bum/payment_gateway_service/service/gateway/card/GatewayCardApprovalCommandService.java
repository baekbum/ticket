package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.CardCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GatewayCardApprovalCommandService {

    // 현재 등록된 인증 정보와 신규 실행 허용 상태를 확인한다.
    private final MerchantJpaRepository merchants;
    // 원거래는 HTTP 전후의 짧은 PG 트랜잭션에서만 잠근다.
    private final GatewayPaymentJpaRepository payments;
    // APPROVE/INITIAL은 거래마다 한 번만 확정한다.
    private final GatewayPaymentHistoryJpaRepository histories;
    // 승인 상태·이력과 같은 트랜잭션에 저장하는 가맹점 통보.
    private final GatewayCardNotificationJpaRepository notifications;

    /**
     * 원거래와 카드 요청을 검증하고 저장된 승인 또는 금융 요청용 불변 값을 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayCardApprovalPreparation inspect(
            AuthenticatedMerchant principal,
            String pgPaymentId,
            GatewayCardApprovalRequest request
    ) {

        Merchant merchant = merchant(principal, request.merchantId());
        GatewayPayment payment = owned(principal, pgPaymentId);
        validateRequest(payment, request);
        FinancialCardApproveRequest input = new FinancialCardApproveRequest(pgPaymentId, payment.getBuyerReference(),
                request.cardCompany(), request.cardNumber(), request.cvc(), request.cardPassword(),
                payment.getAmount(), payment.getExpiresAt());
        Optional<GatewayPaymentHistory> existing = approved(pgPaymentId);

        if (existing.isPresent()) {
            validateReplay(payment, existing.get(), request);

            return new GatewayCardApprovalPreparation(input, response(payment, existing.get()),
                    merchant.getStatus() == MerchantStatus.ACTIVE,
                    merchant.getNotificationContract(), merchant.getNotificationTargetKey());
        }

        if (payment.getStatus() != GatewayPaymentStatus.READY) {
            throw new GatewayPaymentConflictException("새 카드 승인을 실행할 수 없는 PG 상태입니다.");
        }

        return new GatewayCardApprovalPreparation(input, null, merchant.getStatus() == MerchantStatus.ACTIVE,
                merchant.getNotificationContract(), merchant.getNotificationTargetKey());
    }

    /**
     * 가맹점 범위로 승인 결과와 통보 상태를 조회한다. 금융 HTTP는 수행하지 않는다.
     */
    @Transactional(readOnly = true)
    public GatewayCardApprovalResponse find(AuthenticatedMerchant principal, String pgPaymentId) {

        merchant(principal, null);
        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(pgPaymentId, principal.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        return response(payment, approved(pgPaymentId).orElseThrow(GatewayPaymentNotFoundException::new));
    }

    /**
     * 금융 원승인을 대조하고 PG 완료·승인 이력·통보 Outbox를 하나의 트랜잭션으로 확정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayCardApprovalResponse confirm(
            AuthenticatedMerchant principal,
            String pgPaymentId,
            GatewayCardApprovalRequest request,
            FinancialCardApprovalResponse card
    ) {

        merchant(principal, request.merchantId());
        GatewayPayment payment = owned(principal, pgPaymentId);
        validateRequest(payment, request);
        validateCard(payment, request, card);
        Optional<GatewayPaymentHistory> existing = approved(pgPaymentId);

        if (existing.isPresent()) {
            validateReplay(payment, existing.get(), request);

            if (!existing.get().getDetails().get("transactionId").equals(card.transactionId())) {
                throw new FinancialProviderResponseException();
            }

            return response(payment, existing.get());
        }

        if (payment.getStatus() != GatewayPaymentStatus.READY) {
            throw new GatewayPaymentConflictException("PG 상태가 바뀌어 카드 승인을 확정할 수 없습니다.");
        }

        payment.approveCard(card.cardCompany(), card.approvedAt());
        GatewayPaymentHistory history = histories.saveAndFlush(
                GatewayPaymentHistory.cardApproved(payment, card, fingerprint(payment, request)));
        notifications.saveAndFlush(GatewayCardNotification.pending(payment, card));

        return response(payment, history);
    }

    /**
     * 조회 경로에서는 승인 이력이 없다는 사실과 잘못된 소유권을 구분해 내부에서 처리한다.
     */
    @Transactional(readOnly = true)
    public Optional<GatewayCardApprovalResponse> findApproved(AuthenticatedMerchant principal, String id) {

        merchant(principal, null);
        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(id, principal.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD) {
            throw new GatewayPaymentConflictException("카드 거래가 아닙니다.");
        }

        return approved(id).map(history -> response(payment, history));
    }

    /**
     * 카드 원문 없이 금융 서버의 인증된 원승인으로 PG 저장 실패를 복구한다. 새 승인은 실행하지 않는다.
     */
    @Transactional
    public GatewayCardApprovalResponse confirmRecovered(
            AuthenticatedMerchant principal,
            String id,
            FinancialCardApprovalResponse card
    ) {

        merchant(principal, null);
        GatewayPayment payment = owned(principal, id);

        if (card == null || payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                || !id.equals(card.pgPaymentId()) || !payment.getBuyerReference().equals(card.buyerReference())
                || card.amount() == null || payment.getAmount().compareTo(card.amount()) != 0
                || card.cardIdentity() == null || !card.cardIdentity().matches("[0-9a-f]{64}")
                || card.cardCompany() == null || card.transactionId() == null || card.transactionId().isBlank()
                || card.transactionId().length() > 80 || card.maskedCardNumber() == null
                || !card.maskedCardNumber().matches("[0-9]{4}-\\*{4}-\\*{4}-[0-9]{4}")
                || card.approvedAt() == null || card.approvedAt().isAfter(payment.getExpiresAt())
                || card.status() != CardPaymentHistoryStatus.APPROVED) {
            throw new FinancialProviderResponseException();
        }

        String hash = CardCredentials.hash(payment.getCreationRequestHash() + "|" + card.cardIdentity());
        Optional<GatewayPaymentHistory> previous = approved(id);

        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getRequestHash())
                    || !card.transactionId().equals(previous.get().getDetails().get("transactionId"))) {
                throw new FinancialProviderResponseException();
            }

            return response(payment, previous.get());
        }

        payment.approveCard(card.cardCompany(), card.approvedAt());
        GatewayPaymentHistory history = histories.saveAndFlush(GatewayPaymentHistory.cardApproved(payment, card, hash));
        notifications.saveAndFlush(GatewayCardNotification.pending(payment, card));

        return response(payment, history);
    }

    /**
     * 본문 코드가 아닌 principal 자격증명으로 현재 가맹점을 확인한다.
     */
    private Merchant merchant(AuthenticatedMerchant principal, Long bodyMerchantId) {

        if (principal == null || principal.merchantId() == null
                || (bodyMerchantId != null && !bodyMerchantId.equals(principal.merchantId()))) {
            throw new AccessDeniedException("인증된 가맹점 정보가 일치하지 않습니다.");
        }

        return merchants.findById(principal.merchantId())
                .orElseThrow(() -> new AccessDeniedException("가맹점 인증 정보가 변경되었습니다."));
    }

    /**
     * 다른 가맹점 거래와 없는 거래의 존재 여부를 구분해서 노출하지 않는다.
     */
    private GatewayPayment owned(AuthenticatedMerchant principal, String pgPaymentId) {

        return payments.findOwnedForUpdate(principal.merchantId(), pgPaymentId)
                .orElseThrow(GatewayPaymentNotFoundException::new);
    }

    /**
     * 요청 수단에 의존하지 않고 저장된 원거래의 카드 수단·예매자·금액을 대조한다.
     */
    private void validateRequest(GatewayPayment payment, GatewayCardApprovalRequest request) {

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                || !payment.getBuyerReference().equals(request.buyerReference())
                || request.amount() == null || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new GatewayPaymentConflictException("카드 승인 요청이 준비된 PG 원거래와 일치하지 않습니다.");
        }

        CardCredentials.identity(request.cardCompany(), request.cardNumber());
    }

    /**
     * 금융 결과가 다른 거래나 다른 카드의 응답이면 PG 성공으로 반영하지 않는다.
     */
    private void validateCard(
            GatewayPayment payment,
            GatewayCardApprovalRequest request,
            FinancialCardApprovalResponse card
    ) {

        if (card == null || !payment.getPgPaymentId().equals(card.pgPaymentId())
                || !payment.getBuyerReference().equals(card.buyerReference())
                || card.amount() == null || payment.getAmount().compareTo(card.amount()) != 0) {
            throw new FinancialProviderResponseException();
        }

        if (!CardCredentials.identity(request.cardCompany(), request.cardNumber()).equals(card.cardIdentity())) {
            throw new GatewayPaymentConflictException("기존 금융 승인과 요청의 거래·금액·카드가 일치하지 않습니다.");
        }

        if (card.status() != CardPaymentHistoryStatus.APPROVED || card.cardCompany() != request.cardCompany()
                || card.transactionId() == null || card.transactionId().isBlank() || card.transactionId().length() > 80
                || !CardCredentials.mask(CardCredentials.normalize(request.cardNumber())).equals(card.maskedCardNumber())
                || card.approvedAt() == null) {
            throw new FinancialProviderResponseException();
        }
    }

    /**
     * 동일 카드 승인 작업을 조회한다. 준비 REGISTER 이력과 구분한다.
     */
    private Optional<GatewayPaymentHistory> approved(String pgPaymentId) {

        return histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                pgPaymentId, GatewayPaymentOperation.APPROVE, "INITIAL");
    }

    /**
     * 성공 응답 재전송 전에도 같은 카드 요청인지 확인한다.
     */
    private void validateReplay(
            GatewayPayment payment,
            GatewayPaymentHistory history,
            GatewayCardApprovalRequest request
    ) {

        if (!fingerprint(payment, request).equals(history.getRequestHash())) {
            throw new GatewayPaymentConflictException("동일 PG 거래의 최초 승인 카드와 일치하지 않습니다.");
        }
    }

    /**
     * 비밀 인증값을 제외하고 원거래와 카드 식별자만 작업 해시에 포함한다.
     */
    private String fingerprint(GatewayPayment payment, GatewayCardApprovalRequest request) {

        return CardCredentials.hash(payment.getCreationRequestHash() + "|"
                + CardCredentials.identity(request.cardCompany(), request.cardNumber()));
    }

    /**
     * 최초 원승인 스냅샷과 현재 PG/통보 상태를 함께 반환한다.
     */
    private GatewayCardApprovalResponse response(GatewayPayment payment, GatewayPaymentHistory history) {

        GatewayCardNotification notification = notifications.findByPaymentPgPaymentId(payment.getPgPaymentId())
                .orElseThrow(FinancialProviderResponseException::new);

        return new GatewayCardApprovalResponse(payment.getPgPaymentId(), payment.getMerchant().getMerchantId(),
                payment.getMerchantPaymentNo(), payment.getBuyerReference(),
                CardCompany.valueOf((String) history.getDetails().get("cardCompany")),
                (String) history.getDetails().get("transactionId"), (String) history.getDetails().get("maskedCardNumber"),
                payment.getAmount(), LocalDateTime.parse((String) history.getDetails().get("approvedAt")),
                payment.getStatus(), notification.getStatus());
    }
}
