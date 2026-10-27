package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/** 결제 요청 확정 시점의 계산 근거. 생성 후 변경하지 않는다. */
@Entity
@Table(name = "payment_snapshots")
@Getter
@NoArgsConstructor
public class PaymentSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentSnapshotId;

    @Column(nullable = false, updatable = false)
    private Integer amount;

    @Column(nullable = false, updatable = false)
    private int reservationFeeAmount;

    @Column(nullable = false, updatable = false)
    private int deliveryFeeAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    @ElementCollection
    @CollectionTable(
            name = "payment_snapshot_tickets",
            joinColumns = @JoinColumn(name = "payment_snapshot_id"))
    @OrderColumn(name = "item_order")
    private List<PaymentSnapshotTicket> tickets = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "payment_snapshot_discounts",
            joinColumns = @JoinColumn(name = "payment_snapshot_id"))
    @OrderColumn(name = "item_order")
    private List<PaymentSnapshotDiscount> discounts = new ArrayList<>();

    @Builder
    public PaymentSnapshot(
            Integer amount,
            Integer reservationFeeAmount,
            Integer deliveryFeeAmount,
            List<Ticket> tickets,
            List<ReservationDiscount> discounts) {
        this.amount = amount;
        this.reservationFeeAmount = reservationFeeAmount == null ? 0 : reservationFeeAmount;
        this.deliveryFeeAmount = deliveryFeeAmount == null ? 0 : deliveryFeeAmount;
        capturedAt = LocalDateTime.now();
        if (tickets != null)
            tickets.forEach(ticket -> this.tickets.add(new PaymentSnapshotTicket(ticket)));
        if (discounts != null)
            discounts.forEach(
                    discount -> this.discounts.add(new PaymentSnapshotDiscount(discount)));
    }

    public List<PaymentSnapshotTicket> getTickets() {
        return Collections.unmodifiableList(tickets);
    }

    public List<PaymentSnapshotDiscount> getDiscounts() {
        return Collections.unmodifiableList(discounts);
    }
}
