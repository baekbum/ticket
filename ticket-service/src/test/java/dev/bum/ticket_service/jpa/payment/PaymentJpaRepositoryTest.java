package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PaymentJpaRepositoryTest {

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    @Autowired
    private EventJpaRepository eventJpaRepository;

    @Test
    @DisplayName("멱등 키는 결제 준비 건마다 유일해야 한다")
    void idempotency_key_must_be_unique() {
        Event event = eventJpaRepository.save(event());
        Reservation firstReservation = reservationJpaRepository.save(reservation("order-1", "user01", event));
        Reservation secondReservation = reservationJpaRepository.save(reservation("order-2", "user01", event));

        paymentJpaRepository.save(payment(firstReservation, "PAY-1", "idem-1"));

        assertThatThrownBy(() -> {
            paymentJpaRepository.saveAndFlush(payment(secondReservation, "PAY-2", "idem-1"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("가상계좌 번호는 결제 건마다 유일해야 한다")
    void account_number_must_be_unique() {
        Event event = eventJpaRepository.save(event());
        Reservation firstReservation = reservationJpaRepository.save(reservation("order-1", "user01", event));
        Reservation secondReservation = reservationJpaRepository.save(reservation("order-2", "user01", event));

        paymentJpaRepository.save(virtualAccountPayment(firstReservation, "PAY-1", "idem-1", "1111-2222"));

        assertThatThrownBy(() -> {
            paymentJpaRepository.saveAndFlush(virtualAccountPayment(secondReservation, "PAY-2", "idem-2", "1111-2222"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Event event() {
        return Event.builder()
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(100)
                .availableSeats(100)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private Reservation reservation(String orderId, String userId, Event event) {
        return Reservation.builder()
                .orderId(orderId)
                .userId(userId)
                .event(event)
                .status(ReservationStatus.PENDING_PAYMENT)
                .tickets(new ArrayList<>())
                .reservedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment payment(Reservation reservation, String paymentNo, String idempotencyKey) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo(paymentNo)
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .amount(180000)
                .idempotencyKey(idempotencyKey)
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .build();
    }

    private Payment virtualAccountPayment(Reservation reservation, String paymentNo, String idempotencyKey, String accountNumber) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo(paymentNo)
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .idempotencyKey(idempotencyKey)
                .bankName("KB")
                .accountNumber(accountNumber)
                .depositorName("user01")
                .requestedAt(LocalDateTime.of(2026, 7, 27, 12, 0))
                .expiresAt(LocalDateTime.of(2026, 7, 28, 12, 0))
                .build();
    }
}
