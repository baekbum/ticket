package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayVirtualAccountApiService {

    // 기존 계좌 API의 금융 응답과 외부 PG 응답을 명시적으로 변환한다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 기존 발급 결과의 JSON 필드를 유지하고 금융 DTO를 컨트롤러에 노출하지 않는다.
     */
    public GatewayVirtualAccountIssueResponse issue(GatewayVirtualAccountIssueRequest request) {

        FinancialVirtualAccountIssueResponse response = financial.issueAccount(request);
        validatePaymentNo(request.getPaymentNo(), response);

        return GatewayVirtualAccountIssueResponse.builder().paymentNo(response.paymentNo())
                .bankCompany(response.bankCompany()).bankName(response.bankName()).accountNumber(response.accountNumber())
                .depositorName(response.depositorName()).amount(response.amount()).expiresAt(response.expiresAt())
                .status(response.status()).issued(response.issued()).message(response.message()).build();
    }

    /**
     * 기존 Ticksy 상태 조회 DTO를 유지한다.
     */
    public GatewayVirtualAccountStatusResponse status(String paymentNo) {

        FinancialVirtualAccountStatusResponse response = financial.accountStatus(paymentNo);
        validatePaymentNo(paymentNo, response);

        return new GatewayVirtualAccountStatusResponse(response.paymentNo(), response.bankCompany(), response.bankName(),
                response.accountNumber(), response.depositorName(), response.amount(), response.expiresAt(),
                response.depositedAt(), response.status());
    }

    /**
     * 같은 환불 프로세스 ID를 보내고 환불 결과의 기존 필드를 반환한다.
     */
    public GatewayVirtualAccountRefundResponse refund(GatewayVirtualAccountRefundRequest request) {

        FinancialVirtualAccountRefundResponse response = financial.refundAccount(request);
        validatePaymentNo(request.getPaymentNo(), response);

        return GatewayVirtualAccountRefundResponse.builder().paymentNo(response.paymentNo())
                .refundBankCompany(response.refundBankCompany()).refundAccountNumber(response.refundAccountNumber())
                .refundAccountHolder(response.refundAccountHolder()).refundedAmount(response.refundedAmount())
                .message(response.message()).build();
    }

    /**
     * 기존 취소 응답 필드와 중복 취소의 성공 계약을 유지한다.
     */
    public GatewayVirtualAccountCancelResponse cancel(GatewayVirtualAccountCancelRequest request) {

        FinancialVirtualAccountCancelResponse response = financial.cancelAccount(request);
        validatePaymentNo(request.paymentNo(), response);

        return new GatewayVirtualAccountCancelResponse(response.paymentNo(), response.bankName(),
                response.accountNumber(), response.amount(), response.status());
    }

    /**
     * 원래 요청의 결제번호와 다른 금융 응답을 PG 응답으로 변환하지 않는다.
     */
    private void validatePaymentNo(String expected, FinancialPaymentResponse response) {

        if (!expected.equals(response.paymentNo())) {
            throw new FinancialProviderResponseException();
        }
    }
}
