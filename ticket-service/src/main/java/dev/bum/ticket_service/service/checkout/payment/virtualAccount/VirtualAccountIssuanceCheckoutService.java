package dev.bum.ticket_service.service.checkout.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.exception.payment.GatewayAccountIssueUnconfirmedException;
import dev.bum.ticket_service.feign.paymentgateway.*;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class VirtualAccountIssuanceCheckoutService {

    // 최초 1회와 추가 재시도 3회를 합친 최대 시도 횟수.
    private static final int MAX_ATTEMPTS = 4;
    // HTTP 호출 전후의 짧은 로컬 트랜잭션.
    private final VirtualAccountIssuanceCheckoutCommandService commands;
    // 인증된 Ticksy 가맹점 자격증명으로 신규 PG 계약을 호출한다.
    private final PaymentGatewayTransactionClient gateway;

    /**
     * PG 원거래를 준비하고 식별자를 로컬에 커밋한다. 기존 식별자가 있으면 그대로 반환한다.
     */
    public PaymentResponse prepare(String paymentNo) {

        return executeWithRetry(paymentNo, "거래 준비", () -> prepareOnce(paymentNo));
    }

    /**
     * 준비된 PG 원거래로 계좌를 발급한다. 이 단계에서는 PG prepare를 호출하지 않는다.
     */
    public PaymentResponse issue(String paymentNo) {

        return executeWithRetry(paymentNo, "계좌 발급", () -> issueOnce(paymentNo));
    }

    /**
     * 각 단계에서 일시적인 실패만 최초 1회와 추가 3회까지 시도한다.
     */
    private PaymentResponse executeWithRetry(String paymentNo, String operation, Supplier<PaymentResponse> action) {

        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException failure) {
                if (!isRetryable(failure)) {
                    throw failure;
                }

                if (attempt == MAX_ATTEMPTS || Thread.currentThread().isInterrupted()) {
                    throw new GatewayAccountIssueUnconfirmedException(failure);
                }

                log.warn("PG {} 요청 재시도: paymentNo={}, completedAttempts={}, errorType={}",
                        operation, paymentNo, attempt, failure.getClass().getSimpleName());
            }
        }
    }

    /**
     * 매 준비 시도에서 최신 연결 상태를 읽고 같은 최초 조건으로 PG 응답 유실을 복구한다.
     */
    private PaymentResponse prepareOnce(String paymentNo) {

        VirtualAccountIssuePreparation preparation = commands.findPreparation(paymentNo);

        if (preparation.pgPaymentId() != null || !preparation.pending()) {
            return preparation.payment();
        }

        return commands.prepared(paymentNo, gateway.prepare(preparation.request()));
    }

    /**
     * 매 발급 시도에서 커밋된 PG ID와 최초 조건을 다시 읽어 같은 거래의 결과를 복구한다.
     */
    private PaymentResponse issueOnce(String paymentNo) {

        VirtualAccountIssuePreparation preparation = commands.findPreparation(paymentNo);

        if (preparation.pgPaymentId() == null) {
            throw new IllegalStateException("가상계좌 발급 전에 PG 거래 준비가 필요합니다.");
        }

        if (!preparation.pending()) {
            return preparation.payment();
        }

        GatewayPaymentPrepareRequest request = preparation.request();
        GatewayAccountIssueRequest issue = new GatewayAccountIssueRequest(request.merchantId(),
                request.buyerReference(), request.amount(), request.bankCompany());
        GatewayAccountIssueResponse response = gateway.issue(preparation.pgPaymentId(), issue);

        return commands.issued(paymentNo, response);
    }

    /**
     * 통신 실패·PG 5xx·일시적인 DB 오류만 재시도한다. 4xx·계약 불일치·영구 저장 오류는 바로 반환한다.
     */
    private boolean isRetryable(RuntimeException failure) {

        if (failure instanceof FeignException http) {
            return (http instanceof RetryableException && http.status() < 0)
                    || (http.status() >= 500 && http.status() < 600);
        }

        return failure instanceof TransientDataAccessException;
    }
}
