package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

/** 결제 확정 당시 티켓 가격. */
@Embeddable
@Getter
@NoArgsConstructor
public class PaymentSnapshotTicket {
    private Long ticketId;

    @Column(nullable = false)
    private int ticketPrice;

    public PaymentSnapshotTicket(Ticket ticket) {
        ticketId = ticket.getTicketId();
        ticketPrice = ticket.getPrice() == null ? 0 : ticket.getPrice();
    }
}
