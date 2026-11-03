package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

/** 결제 확정 당시 티켓 가격. */
@Embeddable
@Getter
@NoArgsConstructor
public class PaymentSnapshotTicket {

    // 티켓 식별자.
    private Long ticketId;

    // 티켓 가격.
    @Column(nullable = false)
    private int ticketPrice;

    // 전달받은 값으로 결제 티켓 스냅샷 객체를 초기화한다.
    public PaymentSnapshotTicket(Ticket ticket) {

        ticketId = ticket.getTicketId();
        ticketPrice = ticket.getPrice() == null ? 0 : ticket.getPrice();
    }
}
