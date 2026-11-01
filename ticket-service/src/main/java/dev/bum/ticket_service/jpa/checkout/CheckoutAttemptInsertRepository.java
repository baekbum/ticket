package dev.bum.ticket_service.jpa.checkout;

public interface CheckoutAttemptInsertRepository {

    /** 주문이 없으면 준비 건과 좌석 목록을 함께 저장하고 1, 이미 있으면 0을 반환한다. */
    int insertIfAbsent(CheckoutAttempt attempt);
}
