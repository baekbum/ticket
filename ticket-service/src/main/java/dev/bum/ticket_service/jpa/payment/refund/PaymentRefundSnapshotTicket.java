package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundSnapshotTicketResponse;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

/** 환불 대상 티켓의 가격 스냅샷. */
@Entity
@Table(name = "payment_refund_snapshot_tickets")
@Getter
@NoArgsConstructor
public class PaymentRefundSnapshotTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_ticket_id")
    private Long paymentRefundSnapshotTicketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_snapshot_id", nullable = false)
    private PaymentRefundSnapshot snapshot;

    @Column(nullable = false, updatable = false)
    private Long ticketId;

    @Column(nullable = false, updatable = false)
    private Integer ticketPrice;

    public static PaymentRefundSnapshotTicket capture(
            PaymentRefundSnapshot snapshot, Ticket ticket) {
        PaymentRefundSnapshotTicket result = new PaymentRefundSnapshotTicket();
        result.snapshot = snapshot;
        result.ticketId = ticket.getTicketId();
        result.ticketPrice = ticket.getPrice() == null ? 0 : ticket.getPrice();
        return result;
    }

    public PaymentRefundSnapshotTicketResponse toResponse() {
        return PaymentRefundSnapshotTicketResponse.builder()
                .paymentRefundSnapshotTicketId(paymentRefundSnapshotTicketId)
                .ticketId(ticketId)
                .ticketPrice(ticketPrice)
                .build();
    }
}
