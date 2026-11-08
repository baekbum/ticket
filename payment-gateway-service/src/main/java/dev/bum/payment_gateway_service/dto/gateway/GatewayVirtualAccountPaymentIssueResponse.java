package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPayment;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayVirtualAccountPaymentIssueResponse(
        // PG가 발급한 원거래 식별자.
        String pgPaymentId,

        // 인증된 소유 가맹점.
        Long merchantId,

        // 가맹점 안에서 사용하는 결제번호.
        String paymentNo,

        // 준비 시 확인한 예매자.
        String buyerReference,

        // 실제 발급한 은행.
        BankCompany bankCompany,

        // 발급 시 은행 이름.
        String bankName,

        // 최초 발급한 계좌번호. 재요청에서도 유지한다.
        String accountNumber,

        // 원거래의 입금 금액.
        BigDecimal amount,

        // 준비 시 결정한 입금 기한.
        LocalDateTime expiresAt,

        // PG의 현재 거래 상태.
        GatewayPaymentStatus pgStatus,

        // 최초 발급 결과의 은행 상태. PG 상태나 통보 완료 상태와 구분한다.
        VirtualAccountPaymentStatus bankStatus
) {

    // PG 거래와 검증한 은행 결과를 합쳐 가맹점에 반환한다.
    public static GatewayVirtualAccountPaymentIssueResponse from(
            GatewayPayment payment,
            BankVirtualAccountIssueResponse bank
    ) {

        return new GatewayVirtualAccountPaymentIssueResponse(payment.getPgPaymentId(),
                payment.getMerchant().getMerchantId(), payment.getMerchantPaymentNo(), payment.getBuyerReference(),
                bank.bankCompany(), bank.bankName(), bank.accountNumber(), payment.getAmount(), payment.getExpiresAt(),
                payment.getStatus(), bank.status());
    }
}
