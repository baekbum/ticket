package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.feign.paymentgateway.GatewayPaymentPrepareResponse;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.service.payment.payment.PaymentExpirationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class GatewayVirtualAccountExpirationCommands {

    // 조회 후보를 짧은 로컬 트랜잭션으로 읽는다.
    private final PaymentJpaRepository payments;
    // 최종 반영은 입금·취소와 같은 예약 → 결제 순서로 잠근다.
    private final VirtualAccountPaymentLockService locks;
    // 예매·티켓·Payment·좌석·쿠폰의 기존 원자적 만료 처리를 재사용한다.
    private final PaymentExpirationService expirations;
    // 이 Ticksy 서버에 등록한 PG 가맹점 코드.
    private final long merchantId;

    /**
     * 서버에 등록된 가맹점과 기존 만료 처리 경계를 연결한다.
     */
    public GatewayVirtualAccountExpirationCommands(
            PaymentJpaRepository payments,
            VirtualAccountPaymentLockService locks,
            PaymentExpirationService expirations,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드가 필요합니다.");
        }

        this.payments = payments;
        this.locks = locks;
        this.expirations = expirations;
        this.merchantId = merchantId;
    }

    /**
     * 시간은 후보 선정에만 사용하고 금융 상태를 독자적으로 만료시키지 않는다.
     */
    @Transactional(readOnly = true)
    public GatewayVirtualAccountExpirationCandidate findCandidate(Long paymentId, LocalDateTime now) {

        Payment payment = payments.findById(paymentId).orElse(null);

        if (!isPending(payment) || payment.getGatewayExpiresAt() == null
                || payment.getGatewayExpiresAt().isAfter(now)) {
            return null;
        }

        return new GatewayVirtualAccountExpirationCandidate(paymentId, payment.getPaymentNo(), payment.getPgPaymentId());
    }

    /**
     * PG가 은행 만료를 확정한 거래만 로컬에서 만료한다. 늦은 조회 결과가 입금·취소를 덮어쓰지 않는다.
     */
    public boolean expireIfConfirmed(
            GatewayVirtualAccountExpirationCandidate candidate,
            GatewayPaymentPrepareResponse response
    ) {

        Payment payment = locks.lock(candidate.paymentNo());

        if (!candidate.paymentId().equals(payment.getPaymentId()) || response == null
                || !Long.valueOf(merchantId).equals(response.merchantId())
                || !payment.getPaymentNo().equals(response.paymentNo())
                || !payment.getReservation().getUserId().equals(response.buyerReference())
                || response.paymentMethod() != PaymentMethod.BANK_TRANSFER
                || response.bankCompany() != payment.getGatewayBankCompany()
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0
                || !Objects.equals(payment.getGatewayExpiresAt(), response.expiresAt())
                || response.pgPaymentId() == null
                || (payment.getPgPaymentId() != null && !payment.getPgPaymentId().equals(response.pgPaymentId()))
                || (candidate.pgPaymentId() != null && !candidate.pgPaymentId().equals(response.pgPaymentId()))) {
            throw new IllegalArgumentException("PG 만료 조회 결과가 가맹점 원거래와 일치하지 않습니다.");
        }

        if (!isPending(payment) || !"EXPIRED".equals(response.status())) {
            return false;
        }

        payment.bindGatewayPayment(response.pgPaymentId());
        expirations.expire(payment);

        return true;
    }

    /**
     * 신규 무통장의 미완료 상태만 대상으로 삼는다. PAID·환불·취소·기존 만료는 유지한다.
     */
    private boolean isPending(Payment payment) {

        return payment != null && payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && payment.getGatewayBankCompany() != null
                && (payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.WAITING_DEPOSIT);
    }
}
