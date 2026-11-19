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

    // 환불 시점 티켓 스냅샷 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_history_ticket_id")
    private Long paymentRefundSnapshotTicketId;

    // 처리 시점의 데이터 스냅샷.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_snapshot_id", nullable = false)
    private PaymentRefundSnapshot snapshot;

    // 티켓 식별자.
    @Column(nullable = false, updatable = false)
    private Long ticketId;

    // 티켓 가격.
    @Column(nullable = false, updatable = false)
    private Integer ticketPrice;

    // 처리 시점의 데이터를 스냅샷으로 저장한다.
    public static PaymentRefundSnapshotTicket capture(
            PaymentRefundSnapshot snapshot,
            Ticket ticket
    ) {

        PaymentRefundSnapshotTicket result = new PaymentRefundSnapshotTicket();
        result.snapshot = snapshot;
        result.ticketId = ticket.getTicketId();
        result.ticketPrice = ticket.getPrice() == null ? 0 : ticket.getPrice();
        return result;
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    public PaymentRefundSnapshotTicketResponse toResponse() {

        return PaymentRefundSnapshotTicketResponse.builder()
                .paymentRefundSnapshotTicketId(paymentRefundSnapshotTicketId)
                .ticketId(ticketId)
                .ticketPrice(ticketPrice)
                .build();
    }
}
