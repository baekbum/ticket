package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class VirtualAccountReconciliationValidator {

    private final TicketRepository tickets;

    public String problem(Payment payment, GatewayVirtualAccountStatusResponse status) {
        if (status == null || !payment.getPaymentNo().equals(status.paymentNo())) {
            return "PG 결제번호가 일치하지 않습니다.";
        }
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER
                || status.amount() == null
                || BigDecimal.valueOf(payment.getAmount()).compareTo(status.amount()) != 0
                || !StringUtils.hasText(payment.getAccountNumber())
                || !payment.getAccountNumber().equals(status.accountNumber())
                || !StringUtils.hasText(payment.getBankName())
                || !payment.getBankName().equals(status.bankName())
                || status.bankCompany() == null
                || !status.bankCompany().getBankName().equals(status.bankName())
                || payment.getExpiresAt() == null
                || !payment.getExpiresAt().equals(status.expiresAt())) {
            return "Ticket과 PG의 결제 수단·금액·계좌 정보가 일치하지 않습니다.";
        }
        if (status.status() != GatewayVirtualAccountStatus.DEPOSITED
                && status.status() != GatewayVirtualAccountStatus.TICKET_PAYMENT_FAILED
                && status.status() != GatewayVirtualAccountStatus.TICKET_PAYMENT_COMPLETED) {
            return "PG에서 입금 완료가 확인되지 않았습니다.";
        }
        if (!StringUtils.hasText(status.depositorName()) || status.depositedAt() == null || payment.getExpiresAt() == null
                || status.depositedAt().isAfter(payment.getExpiresAt())) {
            return "입금 시각이 없거나 입금 기한을 지났습니다.";
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return "Ticket 결제가 이미 완료되었습니다.";
        }
        if (payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            return "Ticket 결제가 입금 대기 상태가 아닙니다. 만료·취소 건은 별도 조치가 필요합니다.";
        }
        if (payment.getReservation() == null
                || payment.getReservation().getStatus() != ReservationStatus.PENDING_PAYMENT) {
            return "예약이 결제 대기 상태가 아닙니다.";
        }
        var reservedTickets = tickets.selectByReservation(payment.getReservation());
        if (reservedTickets.isEmpty() || reservedTickets.stream().anyMatch(ticket ->
                ticket.getStatus() != TicketStatus.PENDING_PAYMENT
                        || ticket.getSeat() == null || ticket.getSeat().getStatus() != SeatStatus.LOCKED)) {
            return "티켓 또는 좌석 상태가 결제 대기 상태가 아닙니다.";
        }
        return null;
    }
}
