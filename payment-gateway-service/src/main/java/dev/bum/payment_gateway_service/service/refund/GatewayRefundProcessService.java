package dev.bum.payment_gateway_service.service.refund;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** 호출자가 원래 결제 행을 잠근 A 안에서 환불 요청을 준비한다. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.MANDATORY)
public class GatewayRefundProcessService {

    // 게이트웨이 환불 데이터를 조회하고 저장하는 레포지토리.
    private final GatewayRefundJpaRepository refunds;
    // 게이트웨이 환불 데이터 생성을 담당하는 도구.
    private final GatewayRefundCreator creator;
    // 게이트웨이 환불 결과와 실패 이력을 기록하는 도구.
    private final GatewayRefundFailureRecorder failureRecorder;

    /**
     * 환불 처리에서 처리에 필요한 상태와 데이터를 준비한다.
     */
    public GatewayRefund prepare(GatewayCardPaymentRefundRequest request) {

        GatewayRefund refund = refunds.findById(request.getPaymentRefundProcessId())
                .orElseGet(() -> loadCreated(creator.create(request)));
        refund.validateRequest(request);
        prepareAttempt(refund);
        return refund;
    }

    /**
     * 환불 처리에서 처리에 필요한 상태와 데이터를 준비한다.
     */
    public GatewayRefund prepare(GatewayVirtualAccountRefundRequest request) {

        GatewayRefund refund = refunds.findById(request.getPaymentRefundProcessId())
                .orElseGet(() -> loadCreated(creator.create(request)));
        refund.validateRequest(request);
        prepareAttempt(refund);
        return refund;
    }

    /**
     * 앞서 생성된 처리 정보를 조회한다.
     */
    private GatewayRefund loadCreated(Long processId) {

        // B 커밋 후 일반 조회한다. 동시 실행은 이미 A의 결제 행 잠금으로 제어한다.
        return refunds.findById(processId)
                .orElseThrow(() -> new IllegalStateException("생성한 PG 환불 요청을 찾을 수 없습니다."));
    }

    /**
     * 요청을 실행할 처리 시도를 준비한다.
     */
    private void prepareAttempt(GatewayRefund refund) {

        if (refund.isSucceeded()) return;
        boolean registered = TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(sync -> sync instanceof RefundFailureSynchronization existing
                        && Objects.equals(existing.refund.getId(), refund.getId()));
        if (!registered) {
            TransactionSynchronizationManager.registerSynchronization(new RefundFailureSynchronization(refund));
        }
        refund.retryRequested();
    }

    private final class RefundFailureSynchronization implements TransactionSynchronization {

        // 환불 정보.
        private final GatewayRefund refund;
        // 동시성 검증에 사용할 예상 버전.
        private final Long expectedVersion;

        /**
         * 전달받은 값으로 환불 객체를 초기화한다.
         */
        private RefundFailureSynchronization(GatewayRefund refund) {

            this.refund = refund;
            expectedVersion = refund.getVersion();
        }

        /**
         * 환불 처리에서 트랜잭션 종료 결과에 따른 후속 처리를 수행한다.
         */
        @Override
        public void afterCompletion(int status) {

            if (status != STATUS_ROLLED_BACK) return;
            // 실제 환불과 성공 상태가 함께 롤백된 다음 별도 트랜잭션에서 실패를 기록한다.
            try {
                failureRecorder.record(
                        refund.getId(), refund.getPaymentNo(), refund.getMethod(), expectedVersion, refund.getFailureReason()
                );
            } catch (RuntimeException e) {
                // 기록이 실패해도 B의 최초 요청이 남아 있으므로 같은 ID로 다시 처리할 수 있다.
                log.error("PG 환불 실패 상태 기록 실패: paymentNo={}, processId={}, errorType={}",
                        refund.getPaymentNo(), refund.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
