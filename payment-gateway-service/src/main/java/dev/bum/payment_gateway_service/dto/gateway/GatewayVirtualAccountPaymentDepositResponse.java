package dev.bum.payment_gateway_service.dto.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.financial.FinancialVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPayment;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayVirtualAccountPaymentDepositResponse(
        // PG가 발급한 거래 식별자.
        @JsonInclude(JsonInclude.Include.NON_NULL) String pgPaymentId,

        // 금융 내부 번호가 아닌 소유 가맹점의 결제번호.
        String paymentNo,

        // 입금 은행 코드.
        BankCompany bankCompany,

        // 입금 은행명.
        String bankName,

        // 입금 계좌.
        String accountNumber,

        // 최초 입금자.
        String depositorName,

        // 최초 입금 금액.
        BigDecimal amount,

        // 은행의 금융 상태.
        VirtualAccountPaymentStatus status,

        // 발급 당시 입금 기한.
        LocalDateTime expiresAt,

        // 최초 은행 입금 시각.
        LocalDateTime depositedAt,

        // PG 거래 상태. 은행 상태 및 통보 상태와 다르다.
        @JsonInclude(JsonInclude.Include.NON_NULL) GatewayPaymentStatus pgStatus,

        // 통보 Outbox의 현재 전달 상태.
        @JsonInclude(JsonInclude.Include.NON_NULL) String notificationStatus,

        // 금융/통보 상태와 별도로 표시할 안내 문구.
        String message
) {

    // 기존 미연결 계좌도 같은 PG 응답 타입으로 변환한다. 없던 PG 전용 필드는 JSON에 추가하지 않는다.
    public static GatewayVirtualAccountPaymentDepositResponse from(FinancialVirtualAccountDepositResponse bank) {

        return new GatewayVirtualAccountPaymentDepositResponse(null, bank.paymentNo(), bank.bankCompany(), bank.bankName(),
                bank.accountNumber(), bank.depositorName(), bank.amount(), bank.status(), bank.expiresAt(),
                bank.depositedAt(), null, null, bank.message());
    }

    // 금융 결과와 PG 확정 결과를 분리해 반환한다.
    public static GatewayVirtualAccountPaymentDepositResponse from(
            GatewayPayment payment,
            BankVirtualAccountDepositResponse bank,
            String notificationStatus
    ) {

        return new GatewayVirtualAccountPaymentDepositResponse(payment.getPgPaymentId(), payment.getMerchantPaymentNo(),
                bank.bankCompany(), bank.bankName(), bank.accountNumber(), bank.depositorName(), bank.amount(), bank.status(),
                bank.expiresAt(), bank.depositedAt(), payment.getStatus(), notificationStatus, "최초 입금 정보가 확인되었습니다.");
    }
}
