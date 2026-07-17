package dev.bum.ticket_service.jpa.reservation.reservationDelivery;

import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
        name = "reservation_deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_deliveries_reservation_id", columnNames = "reservation_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDelivery {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_delivery_id")
    private Long reservationDeliveryId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "recipient_name", nullable = false, length = 30)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "delivery_message", length = 255)
    private String deliveryMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationDeliveryStatus status;

    @Column(length = 50)
    private String carrier;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ReservationDelivery(Reservation reservation, ReservationDeliveryRequest info) {
        this.reservation = reservation;
        this.recipientName = info.getRecipientName();
        this.recipientPhone = info.getRecipientPhone();
        this.zipCode = info.getZipCode();
        this.address = info.getAddress();
        this.detailAddress = info.getDetailAddress();
        this.deliveryMessage = info.getDeliveryMessage();
        this.status = ReservationDeliveryStatus.READY;
    }

    public ReservationDeliveryResponse toResponse() {
        return ReservationDeliveryResponse.builder()
                .reservationDeliveryId(this.reservationDeliveryId)
                .reservationId(this.reservation != null ? this.reservation.getReservationId() : null)
                .recipientName(this.recipientName)
                .recipientPhone(this.recipientPhone)
                .zipCode(this.zipCode)
                .address(this.address)
                .detailAddress(this.detailAddress)
                .deliveryMessage(this.deliveryMessage)
                .status(this.status)
                .carrier(this.carrier)
                .trackingNumber(this.trackingNumber)
                .shippedAt(formatDateTime(this.shippedAt))
                .deliveredAt(formatDateTime(this.deliveredAt))
                .build();
    }

    public void prepare() {
        this.status = ReservationDeliveryStatus.PREPARING;
    }

    public void ship(String carrier, String trackingNumber, LocalDateTime shippedAt) {
        this.status = ReservationDeliveryStatus.SHIPPED;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedAt != null ? shippedAt : LocalDateTime.now();
    }

    public void deliver(LocalDateTime deliveredAt) {
        this.status = ReservationDeliveryStatus.DELIVERED;
        this.deliveredAt = deliveredAt != null ? deliveredAt : LocalDateTime.now();
    }

    public void returnDelivery() {
        this.status = ReservationDeliveryStatus.RETURNED;
    }

    public void cancel() {
        this.status = ReservationDeliveryStatus.CANCELLED;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}
