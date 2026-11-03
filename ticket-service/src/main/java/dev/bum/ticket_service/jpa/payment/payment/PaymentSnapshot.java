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

    // 결제 시점 스냅샷 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentSnapshotId;

    // 금액.
    @Column(nullable = false, updatable = false)
    private Integer amount;

    // 예매 수수료 금액.
    @Column(nullable = false, updatable = false)
    private int reservationFeeAmount;

    // 배송비 금액.
    @Column(nullable = false, updatable = false)
    private int deliveryFeeAmount;

    // 스냅샷 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    // 티켓 목록.
    @ElementCollection
    @CollectionTable(
            name = "payment_snapshot_tickets",
            joinColumns = @JoinColumn(name = "payment_snapshot_id"))
    @OrderColumn(name = "item_order")
    private List<PaymentSnapshotTicket> tickets = new ArrayList<>();

    // 적용한 할인 목록.
    @ElementCollection
    @CollectionTable(
            name = "payment_snapshot_discounts",
            joinColumns = @JoinColumn(name = "payment_snapshot_id"))
    @OrderColumn(name = "item_order")
    private List<PaymentSnapshotDiscount> discounts = new ArrayList<>();

    // 전달받은 값으로 결제 스냅샷 객체를 초기화한다.
    @Builder
    public PaymentSnapshot(
            Integer amount,
            Integer reservationFeeAmount,
            Integer deliveryFeeAmount,
            List<Ticket> tickets,
            List<ReservationDiscount> discounts
    ) {

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

    // 티켓 목록 값을 반환한다.
    public List<PaymentSnapshotTicket> getTickets() {

        return Collections.unmodifiableList(tickets);
    }

    // 적용한 할인 목록 값을 반환한다.
    public List<PaymentSnapshotDiscount> getDiscounts() {

        return Collections.unmodifiableList(discounts);
    }
}
