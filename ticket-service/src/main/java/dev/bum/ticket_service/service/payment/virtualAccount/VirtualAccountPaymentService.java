package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.payment.PaymentCompletionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class VirtualAccountPaymentService {

    // 결제 완료 업무 처리를 담당하는 서비스.
    private final PaymentCompletionService paymentCompletionService;
    // 가상계좌 결제 잠금 업무 처리를 담당하는 서비스.
    private final VirtualAccountPaymentLockService paymentLocks;

    /**
     * PG 통보는 가맹점이 준비한 예매 소유자까지 검증한다.
     */
    public PaymentResponse completeDepositFromGateway(
            VirtualAccountDepositCompleteRequest request,
            String buyerReference
    ) {

        Payment payment = paymentLocks.lock(request.getPaymentNo());

        if (!StringUtils.hasText(buyerReference) || payment.getReservation() == null
                || !buyerReference.equals(payment.getReservation().getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("입금 통보의 예매 소유자가 일치하지 않습니다.");
        }

        validateGatewayVirtualAccountDeposit(payment, request);
        if (isDepositApplied(payment) || isCancelledReservation(payment)) {
            return payment.toResponse();
        }

        return paymentCompletionService.completeDeposit(payment, request.getDepositedAt(), request.getDepositorName());
    }

    /**
     * 게이트웨이의 가상계좌 입금 통지를 검증한다.
     */
    private void validateGatewayVirtualAccountDeposit(Payment payment, VirtualAccountDepositCompleteRequest request) {

        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("무통장 입금 결제 요청이 아닙니다.");
        }

        if (BigDecimal.valueOf(payment.getAmount()).compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("입금 금액이 일치하지 않습니다.");
        }

        if (StringUtils.hasText(payment.getAccountNumber())
                && !payment.getAccountNumber().equals(request.getAccountNumber())) {
            throw new IllegalArgumentException("입금 계좌번호가 일치하지 않습니다.");
        }

        if (!Objects.equals(payment.getBankName(), request.getBankName())
                || request.getBankCompany() == null
                || !Objects.equals(request.getBankName(), request.getBankCompany().getBankName())) {
            throw new IllegalArgumentException("입금 은행 정보가 일치하지 않습니다.");
        }

        // 취소를 먼저 커밋했으면 입금 사실로 예약/티켓을 되살리지 않는다. PG는 이 업무 결과로 계좌를 취소한다.
        if (isCancelledReservation(payment)) return;

        if (isDepositApplied(payment)) {
            // 응답 유실 후 환불까지 진행됐더라도 최초 입금과 같은 요청만 성공으로 반환한다.
            if (!Objects.equals(payment.getDepositorName(), request.getDepositorName())
                    || payment.getPaidAt() == null || request.getDepositedAt() == null
                    || !payment.getPaidAt().truncatedTo(ChronoUnit.MICROS)
                        .equals(request.getDepositedAt().truncatedTo(ChronoUnit.MICROS))) {
                throw new IllegalArgumentException("이미 처리된 입금 정보와 요청이 일치하지 않습니다.");
            }
            return;
        }

        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("입금 처리할 수 없는 결제 상태입니다.");
        }

        if (payment.getExpiresAt() != null && request.getDepositedAt() != null && request.getDepositedAt().isAfter(payment.getExpiresAt())) {
            throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
        }
    }

    /**
     * 가상계좌 입금 결과가 이미 반영되었는지 확인한다.
     */
    private boolean isDepositApplied(Payment payment) {

        return payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED;
    }

    /**
     * 예매가 취소 상태인지 확인한다.
     */
    private boolean isCancelledReservation(Payment payment) {

        return payment.getStatus() == PaymentStatus.CANCELLED
                && payment.getReservation().getStatus() == ReservationStatus.CANCELLED;
    }

}
