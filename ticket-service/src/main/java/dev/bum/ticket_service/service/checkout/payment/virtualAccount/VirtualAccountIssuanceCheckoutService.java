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
     * 같은 HTTP 요청 안에서 일시적인 실패만 추가 3회 재시도한다. 이후에는 브라우저의 같은 키 재요청을 기다린다.
     */
    public PaymentResponse issue(String paymentNo) {

        for (int attempt = 1; ; attempt++) {
            try {
                return issueOnce(paymentNo);
            } catch (RuntimeException failure) {
                if (!isRetryable(failure)) {
                    throw failure;
                }

                if (attempt == MAX_ATTEMPTS || Thread.currentThread().isInterrupted()) {
                    throw new GatewayAccountIssueUnconfirmedException(failure);
                }

                log.warn("PG 계좌 발급 요청 재시도: paymentNo={}, completedAttempts={}, errorType={}",
                        paymentNo, attempt, failure.getClass().getSimpleName());
            }
        }
    }

    /**
     * 매 시도마다 커밋된 PG ID와 최초 조건을 다시 읽어 응답 유실·결과 반영 실패를 같은 거래로 복구한다.
     */
    private PaymentResponse issueOnce(String paymentNo) {

        VirtualAccountIssuePreparation preparation = commands.findPreparation(paymentNo);

        if (!preparation.pending()) {
            return preparation.payment();
        }

        GatewayPaymentPrepareRequest request = preparation.request();
        String pgPaymentId = preparation.pgPaymentId();

        if (pgPaymentId == null) {
            pgPaymentId = commands.prepared(paymentNo, gateway.prepare(request));
        }

        GatewayAccountIssueRequest issue = new GatewayAccountIssueRequest(request.merchantId(),
                request.buyerReference(), request.amount(), request.bankCompany());
        GatewayAccountIssueResponse response = gateway.issue(pgPaymentId, issue);

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
