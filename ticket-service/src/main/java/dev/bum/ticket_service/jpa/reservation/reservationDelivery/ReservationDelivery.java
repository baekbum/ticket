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

    // 배송 정보가 귀속되는 예약. 예약 1건당 배송 스냅샷은 최대 1건만 가진다.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 예약 당시 확정된 수령인 이름. 회원 주소록이 수정되어도 이 값은 변경되지 않는다.
    @Column(name = "recipient_name", nullable = false, length = 30)
    private String recipientName;

    // 예약 당시 확정된 수령인 연락처.
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    // 예약 당시 확정된 배송지 우편번호.
    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    // 예약 당시 확정된 기본 배송지 주소.
    @Column(nullable = false, length = 255)
    private String address;

    // 예약 당시 확정된 상세 주소. 아파트 동/호수 등 선택 입력값이다.
    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    // 배송 기사 또는 운영자가 참고할 요청 메시지.
    @Column(name = "delivery_message", length = 255)
    private String deliveryMessage;

    // 배송 처리 상태. 예약 상태와 분리해서 출고/배송 흐름만 표현한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationDeliveryStatus status;

    // 운송장 등록 시 선택한 택배사 또는 배송사 이름.
    @Column(length = 50)
    private String carrier;

    // 배송 추적에 사용할 운송장 번호.
    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    // 실제 발송 처리된 시각.
    private LocalDateTime shippedAt;

    // 배송 완료 처리된 시각.
    private LocalDateTime deliveredAt;

    // 배송 스냅샷 row가 최초 생성된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 배송 상태, 운송장 정보 등이 마지막으로 변경된 시각.
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

    public void updateDeliveryInfo(ReservationDeliveryRequest info) {
        if (this.status != ReservationDeliveryStatus.READY) {
            throw new IllegalStateException("배송 준비 전 상태에서만 배송지를 변경할 수 있습니다.");
        }

        this.recipientName = info.getRecipientName();
        this.recipientPhone = info.getRecipientPhone();
        this.zipCode = info.getZipCode();
        this.address = info.getAddress();
        this.detailAddress = info.getDetailAddress();
        this.deliveryMessage = info.getDeliveryMessage();
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

    public void updateTracking(String carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
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

    public void ready() {
        this.status = ReservationDeliveryStatus.READY;
        this.carrier = null;
        this.trackingNumber = null;
        this.shippedAt = null;
        this.deliveredAt = null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}
