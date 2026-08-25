package dev.bum.payment_gateway_service.service.card;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundResponse;
import dev.bum.payment_gateway_service.exception.TicketPaymentCompleteException;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.jpa.card.DummyCard;
import dev.bum.payment_gateway_service.jpa.card.DummyCardJpaRepository;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistory;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayCardPaymentService {

    private final DummyCardJpaRepository dummyCardJpaRepository;
    private final DummyCardPaymentHistoryJpaRepository dummyCardPaymentHistoryJpaRepository;
    private final TicketPaymentClient ticketPaymentClient;
    private final PasswordEncoder passwordEncoder;

    @Transactional(noRollbackFor = {TicketPaymentCompleteException.class, IllegalArgumentException.class})
    public GatewayCardPaymentApproveResponse approve(String currentUserId, GatewayCardPaymentApproveRequest request) {
        Optional<DummyCardPaymentHistory> existingHistory =
                dummyCardPaymentHistoryJpaRepository.findByPaymentNo(request.getPaymentNo());
        if (existingHistory.isPresent()) {
            return handleExistingHistory(currentUserId, existingHistory.get(), request);
        }

        validateCurrentUser(currentUserId);
        String cardNumber = normalizeCardNumber(request.getCardNumber());
        validateCardNumber(cardNumber);

        Optional<DummyCard> dummyCardOptional = dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                        currentUserId,
                        request.getCardCompany(),
                        sha256(cardNumber)
                );

        if (dummyCardOptional.isEmpty()) {
            failApproval(null, currentUserId, request, cardNumber, "카드 정보가 일치하지 않습니다.");
            failTicketPayment(currentUserId, request, "카드 정보가 일치하지 않습니다.");
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }

        DummyCard dummyCard = dummyCardOptional.get();
        try {
            validateCard(dummyCard, request, cardNumber);
        } catch (IllegalArgumentException e) {
            failApproval(dummyCard, currentUserId, request, cardNumber, e.getMessage());
            failTicketPayment(currentUserId, request, e.getMessage());
            throw e;
        }

        dummyCard.approve(request.getAmount());
        String transactionId = createTransactionId();
        String maskedCardNumber = maskCardNumber(cardNumber);
        DummyCardPaymentHistory paymentHistory = dummyCardPaymentHistoryJpaRepository.save(
                DummyCardPaymentHistory.approved(
                        dummyCard,
                        request.getPaymentNo(),
                        transactionId,
                        maskedCardNumber,
                        request.getAmount()
                )
        );
        PaymentResponse ticketPayment = completeTicketPayment(paymentHistory, request);

        return GatewayCardPaymentApproveResponse.builder()
                .paymentNo(ticketPayment.getPaymentNo())
                .transactionId(paymentHistory.getTransactionId())
                .userId(dummyCard.getUserId())
                .cardCompany(dummyCard.getCardCompany())
                .maskedCardNumber(paymentHistory.getMaskedCardNumber())
                .approvedAmount(request.getAmount())
                .currentMonthUsedAmount(dummyCard.getCurrentMonthUsedAmount())
                .limitAmount(dummyCard.getLimitAmount())
                .approved(true)
                .message("카드 결제와 티켓 결제 완료 반영이 완료되었습니다.")
                .build();
    }

    @Transactional
    public GatewayCardPaymentRefundResponse refund(GatewayCardPaymentRefundRequest request) {
        DummyCardPaymentHistory paymentHistory = dummyCardPaymentHistoryJpaRepository
                .findByPaymentNoAndTransactionId(request.getPaymentNo(), request.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("카드 결제 승인 이력을 찾을 수 없습니다."));

        validateRefundableHistory(paymentHistory, request);
        paymentHistory.getDummyCard().cancelApproval(request.getRefundAmount());
        paymentHistory.refund(request.getRefundAmount());

        return GatewayCardPaymentRefundResponse.builder()
                .paymentNo(paymentHistory.getPaymentNo())
                .transactionId(paymentHistory.getTransactionId())
                .refundedAmount(request.getRefundAmount())
                .status(paymentHistory.getStatus())
                .message(paymentHistory.getStatus() == CardPaymentHistoryStatus.REFUNDED
                        ? "카드 결제 전체 환불이 완료되었습니다."
                        : "카드 결제 부분 환불이 완료되었습니다.")
                .build();
    }

    private void validateCurrentUser(String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new IllegalArgumentException("사용자 인증 정보가 필요합니다.");
        }
    }

    private GatewayCardPaymentApproveResponse handleExistingHistory(
            String currentUserId,
            DummyCardPaymentHistory paymentHistory,
            GatewayCardPaymentApproveRequest request
    ) {
        validateExistingHistory(currentUserId, paymentHistory, request);
        if (paymentHistory.getStatus() == CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED) {
            return toApproveResponse(paymentHistory, "이미 완료된 카드 결제입니다.");
        }

        throw new IllegalArgumentException("이미 카드 결제 시도가 처리된 결제번호입니다. 새 결제번호로 다시 시도해주세요.");
    }

    private void validateExistingHistory(
            String currentUserId,
            DummyCardPaymentHistory paymentHistory,
            GatewayCardPaymentApproveRequest request
    ) {
        validateCurrentUser(currentUserId);

        if (!currentUserId.equals(paymentHistory.getUserId())) {
            throw new IllegalArgumentException("다른 사용자의 카드 승인 이력입니다.");
        }
        if (paymentHistory.getAmount().compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("기존 카드 승인 금액과 요청 금액이 일치하지 않습니다.");
        }
    }

    private void validateRefundableHistory(
            DummyCardPaymentHistory paymentHistory,
            GatewayCardPaymentRefundRequest request
    ) {
        if (paymentHistory.getStatus() == CardPaymentHistoryStatus.REFUNDED) {
            throw new IllegalArgumentException("이미 환불 완료된 카드 결제입니다.");
        }
        if (paymentHistory.getStatus() != CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED
                && paymentHistory.getStatus() != CardPaymentHistoryStatus.PARTIALLY_REFUNDED) {
            throw new IllegalArgumentException("환불할 수 없는 카드 결제 상태입니다.");
        }
        if (paymentHistory.getDummyCard() == null) {
            throw new IllegalArgumentException("환불할 카드 정보를 찾을 수 없습니다.");
        }
        if (request.getRefundAmount().compareTo(paymentHistory.getRefundableAmount()) > 0) {
            throw new IllegalArgumentException("환불 금액이 남은 카드 승인 금액을 초과했습니다.");
        }
    }

    private void failApproval(
            DummyCard dummyCard,
            String currentUserId,
            GatewayCardPaymentApproveRequest request,
            String cardNumber,
            String failureReason
    ) {
        dummyCardPaymentHistoryJpaRepository.save(
                DummyCardPaymentHistory.approvalFailed(
                        dummyCard,
                        currentUserId,
                        request.getPaymentNo(),
                        request.getCardCompany(),
                        maskCardNumber(cardNumber),
                        request.getAmount(),
                        failureReason
                )
        );
    }

    private PaymentResponse completeTicketPayment(
            DummyCardPaymentHistory paymentHistory,
            GatewayCardPaymentApproveRequest request
    ) {
        try {
            PaymentResponse ticketPayment = ticketPaymentClient.completeCardPayment(
                    CardPaymentCompleteRequest.builder()
                            .paymentNo(request.getPaymentNo())
                            .userId(paymentHistory.getUserId())
                            .amount(request.getAmount())
                            .transactionId(paymentHistory.getTransactionId())
                            .cardCompany(paymentHistory.getCardCompany())
                            .maskedCardNumber(paymentHistory.getMaskedCardNumber())
                            .build()
            );
            paymentHistory.completeTicketPayment(null);
            return ticketPayment;
        } catch (RuntimeException e) {
            String failureReason = "ticket-service 결제 완료 반영 실패: " + e.getMessage();
            paymentHistory.getDummyCard().cancelApproval(request.getAmount());
            paymentHistory.cancel(failureReason);
            failTicketPayment(paymentHistory.getUserId(), request, failureReason);
            throw new TicketPaymentCompleteException("카드 승인 후 ticket-service 반영에 실패해 카드 승인을 취소했습니다. 다시 결제해주세요.", e);
        }
    }

    private void failTicketPayment(String currentUserId, GatewayCardPaymentApproveRequest request, String failureReason) {
        try {
            ticketPaymentClient.failCardPayment(
                    CardPaymentFailRequest.builder()
                            .paymentNo(request.getPaymentNo())
                            .userId(currentUserId)
                            .amount(request.getAmount())
                            .failureReason(failureReason)
                            .build()
            );
        } catch (RuntimeException e) {
            log.warn("ticket-service 카드 결제 실패 반영 실패: paymentNo={}, reason={}", request.getPaymentNo(), e.getMessage());
        }
    }

    private GatewayCardPaymentApproveResponse toApproveResponse(DummyCardPaymentHistory paymentHistory, String message) {
        DummyCard dummyCard = paymentHistory.getDummyCard();
        return GatewayCardPaymentApproveResponse.builder()
                .paymentNo(paymentHistory.getPaymentNo())
                .transactionId(paymentHistory.getTransactionId())
                .userId(dummyCard.getUserId())
                .cardCompany(dummyCard.getCardCompany())
                .maskedCardNumber(paymentHistory.getMaskedCardNumber())
                .approvedAmount(paymentHistory.getAmount())
                .currentMonthUsedAmount(dummyCard.getCurrentMonthUsedAmount())
                .limitAmount(dummyCard.getLimitAmount())
                .approved(true)
                .message(message)
                .build();
    }

    private void validateCard(DummyCard dummyCard, GatewayCardPaymentApproveRequest request, String cardNumber) {
        if (!dummyCard.getCardNumberLast4().equals(cardNumber.substring(cardNumber.length() - 4))) {
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }
        if (!dummyCard.getCustomerName().equals(request.getCustomerName())) {
            throw new IllegalArgumentException("카드 소유자명이 일치하지 않습니다.");
        }
        if (!passwordEncoder.matches(request.getCvc(), dummyCard.getCvcHash())) {
            throw new IllegalArgumentException("카드 CVC가 일치하지 않습니다.");
        }
        if (!passwordEncoder.matches(request.getCardPassword(), dummyCard.getCardPasswordHash())) {
            throw new IllegalArgumentException("카드 비밀번호가 일치하지 않습니다.");
        }
        if (dummyCard.isExpired(LocalDate.now())) {
            throw new IllegalArgumentException("만료된 카드입니다.");
        }
        if (!dummyCard.canPay(request.getAmount())) {
            throw new IllegalArgumentException("카드 결제 한도를 초과했거나 사용할 수 없는 카드입니다.");
        }
    }

    private String normalizeCardNumber(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[^0-9]", "") : "";
    }

    private void validateCardNumber(String cardNumber) {
        if (cardNumber.length() != 16) {
            throw new IllegalArgumentException("카드번호 형식이 올바르지 않습니다.");
        }
    }

    private String createTransactionId() {
        return "CARD-" + UUID.randomUUID();
    }

    private String maskCardNumber(String cardNumber) {
        return cardNumber.substring(0, 4) + "-****-****-" + cardNumber.substring(12);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("카드번호 해시를 생성할 수 없습니다.", e);
        }
    }
}
