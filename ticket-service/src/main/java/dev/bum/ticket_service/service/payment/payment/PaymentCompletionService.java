package dev.bum.ticket_service.service.payment.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    // 티켓 데이터를 조회하고 저장하는 레포지토리.
    private final TicketRepository ticketRepository;
    // 좌석 캐시 업무 처리를 담당하는 서비스.
    private final SeatCacheService seatCacheService;

    /**
     * 결제 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    public PaymentResponse complete(Payment payment, LocalDateTime paidAt) {

        return complete(payment, paidAt, null);
    }

    /**
     * 결제 처리에서 가상계좌 입금 완료 결과를 반영한다.
     */
    public PaymentResponse completeDeposit(Payment payment, LocalDateTime paidAt, String depositorName) {

        return complete(payment, paidAt, depositorName);
    }

    /**
     * 결제 처리에서 카드 결제 완료 결과를 반영한다.
     */
    public PaymentResponse completeCard(
            Payment payment,
            String transactionId,
            CardCompany cardCompany,
            String maskedCardNumber,
            LocalDateTime paidAt
    ) {

        return complete(payment, paidAt, null, CardPaymentInfo.builder()
                .transactionId(transactionId)
                .cardCompany(cardCompany)
                .maskedCardNumber(maskedCardNumber)
                .build());
    }

    /**
     * 결제 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    private PaymentResponse complete(Payment payment, LocalDateTime paidAt, String depositorName) {

        return complete(payment, paidAt, depositorName, null);
    }

    /**
     * 결제 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    private PaymentResponse complete(
            Payment payment,
            LocalDateTime paidAt,
            String depositorName,
            CardPaymentInfo cardPaymentInfo
    ) {

        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.toResponse();
        }

        if (payment.getStatus() != PaymentStatus.READY && payment.getStatus() != PaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("결제 완료 처리할 수 없는 상태입니다.");
        }

        PaymentStatus beforePaymentStatus = payment.getStatus();

        Reservation reservation = payment.getReservation();
        List<Ticket> tickets = ticketRepository.selectByReservation(reservation);
        List<Seat> seats = tickets.stream()
                .map(Ticket::getSeat)
                .collect(Collectors.toList());

        if (payment.getMethod() == PaymentMethod.CREDIT_CARD && cardPaymentInfo != null) {
            payment.completeCard(
                    cardPaymentInfo.getTransactionId(),
                    cardPaymentInfo.getCardCompany(),
                    cardPaymentInfo.getMaskedCardNumber(),
                    paidAt
            );
        } else if (payment.getMethod() == PaymentMethod.BANK_TRANSFER && depositorName != null) {
            payment.completeDeposit(depositorName, paidAt);
        } else {
            payment.complete(paidAt);
        }

        AuditDataMapper.setFieldChange("status", beforePaymentStatus, payment.getStatus());

        reservation.paid();
        for (Ticket ticket : tickets) {
            ticket.paid();
            ticket.getSeat().reserved();
        }

        seatCacheService.syncReservedSeatsAfterCommit(seats, reservation.getUserId(), reservation.getOrderId());
        // 현재는 결제 완료 이벤트를 소비하는 consumer가 없으므로 Kafka 발행을 비활성화한다.
        // 후속 알림/정산/배송 이벤트 consumer를 붙일 때 PaymentEventProducer 호출을 다시 활성화한다.

        return payment.toResponse();
    }
}
