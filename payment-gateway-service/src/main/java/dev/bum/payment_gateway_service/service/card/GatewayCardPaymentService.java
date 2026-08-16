package dev.bum.payment_gateway_service.service.card;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;
import dev.bum.payment_gateway_service.jpa.card.DummyCard;
import dev.bum.payment_gateway_service.jpa.card.DummyCardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

@Service
@Transactional
@RequiredArgsConstructor
public class GatewayCardPaymentService {

    private final DummyCardJpaRepository dummyCardJpaRepository;
    private final PasswordEncoder passwordEncoder;

    public GatewayCardPaymentApproveResponse approve(String currentUserId, GatewayCardPaymentApproveRequest request) {
        validateCurrentUser(currentUserId);

        String cardNumber = normalizeCardNumber(request.getCardNumber());
        validateCardNumber(cardNumber);

        DummyCard dummyCard = dummyCardJpaRepository.findByUserIdAndCardCompanyAndCardNumberHash(
                        currentUserId,
                        request.getCardCompany(),
                        sha256(cardNumber)
                )
                .orElseThrow(() -> new IllegalArgumentException("카드 정보가 일치하지 않습니다."));

        validateCard(dummyCard, request, cardNumber);
        dummyCard.approve(request.getAmount());

        return GatewayCardPaymentApproveResponse.builder()
                .paymentNo(request.getPaymentNo())
                .userId(dummyCard.getUserId())
                .cardCompany(dummyCard.getCardCompany())
                .cardNumberLast4(dummyCard.getCardNumberLast4())
                .approvedAmount(request.getAmount())
                .currentMonthUsedAmount(dummyCard.getCurrentMonthUsedAmount())
                .limitAmount(dummyCard.getLimitAmount())
                .approved(true)
                .message("카드 결제 검증이 완료되었습니다.")
                .build();
    }

    private void validateCurrentUser(String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new IllegalArgumentException("사용자 인증 정보가 필요합니다.");
        }
    }

    private void validateCard(DummyCard dummyCard, GatewayCardPaymentApproveRequest request, String cardNumber) {
        if (!dummyCard.getCardNumberLast4().equals(cardNumber.substring(cardNumber.length() - 4))) {
            throw new IllegalArgumentException("카드 정보가 일치하지 않습니다.");
        }
        if (!dummyCard.getCustomerName().equals(request.getCustomerName())) {
            throw new IllegalArgumentException("카드 소유자명이 일치하지 않습니다.");
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
        if (cardNumber.length() < 12 || cardNumber.length() > 19) {
            throw new IllegalArgumentException("카드번호 형식이 올바르지 않습니다.");
        }
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
