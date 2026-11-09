package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardApiService {

    // 기존 사용자 API의 금융 HTTP 응답을 PG 응답과 분리한다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 기존 카드 승인 응답의 필드를 명시적으로 복사한다. 202와 오류는 별도 DTO 경로로 반환한다.
     */
    public GatewayCardPaymentApproveResponse approve(String userId, GatewayCardPaymentApproveRequest request) {

        FinancialCardPaymentApproveResponse response = financial.approveCard(userId, request);
        validatePaymentNo(request.getPaymentNo(), response);

        if (!userId.equals(response.userId())) {
            throw new FinancialProviderResponseException();
        }

        return GatewayCardPaymentApproveResponse.builder().paymentNo(response.paymentNo())
                .transactionId(response.transactionId()).userId(response.userId()).cardCompany(response.cardCompany())
                .maskedCardNumber(response.maskedCardNumber()).approvedAmount(response.approvedAmount())
                .currentMonthUsedAmount(response.currentMonthUsedAmount()).limitAmount(response.limitAmount())
                .approved(response.approved()).message(response.message()).build();
    }

    /**
     * 인증된 사용자 범위로 조회한 금융 상태를 기존 PG 계약으로 변환한다.
     */
    public GatewayCardPaymentStatusResponse status(String userId, String paymentNo) {

        FinancialCardPaymentStatusResponse response = financial.cardStatus(userId, paymentNo);
        validatePaymentNo(paymentNo, response);

        return new GatewayCardPaymentStatusResponse(response.paymentNo(), response.status(), response.transactionId());
    }

    /**
     * 기존 Ticksy가 기대하는 JSON boolean 응답을 유지한다.
     */
    public Boolean approvalExists(String paymentNo) {

        return financial.approvalExists(paymentNo);
    }

    /**
     * 원래 환불 작업 식별자를 그대로 전달하고 PG 응답 필드만 반환한다.
     */
    public GatewayCardPaymentRefundResponse refund(GatewayCardPaymentRefundRequest request) {

        FinancialCardPaymentRefundResponse response = financial.refundCard(request);
        validatePaymentNo(request.getPaymentNo(), response);

        return GatewayCardPaymentRefundResponse.builder().paymentNo(response.paymentNo())
                .transactionId(response.transactionId()).refundedAmount(response.refundedAmount())
                .status(response.status()).message(response.message()).build();
    }

    /**
     * 다른 원거래의 금융 응답을 요청한 거래의 PG 성공 응답으로 반환하지 않는다.
     */
    private void validatePaymentNo(String expected, FinancialPaymentResponse response) {

        if (!expected.equals(response.paymentNo())) {
            throw new FinancialProviderResponseException();
        }
    }
}
