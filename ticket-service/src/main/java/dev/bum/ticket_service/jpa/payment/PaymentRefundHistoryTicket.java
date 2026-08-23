package dev.bum.ticket_service.jpa.payment;

import dev.bum.ticket_service.jpa.ticket.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payment_refund_history_tickets",
        indexes = {
                @Index(name = "idx_payment_refund_history_tickets_history_id", columnList = "payment_refund_history_id"),
                @Index(name = "idx_payment_refund_history_tickets_ticket_id", columnList = "ticket_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundHistoryTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_ticket_id")
    private Long paymentRefundHistoryTicketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_refund_history_id", nullable = false)
    private PaymentRefundHistory paymentRefundHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "ticket_price", nullable = false)
    private Integer ticketPrice;

    public static PaymentRefundHistoryTicket create(PaymentRefundHistory paymentRefundHistory, Ticket ticket) {
        return PaymentRefundHistoryTicket.builder()
                .paymentRefundHistory(paymentRefundHistory)
                .ticket(ticket)
                .ticketPrice(ticket.getPrice())
                .build();
    }
}
