package dev.bum.ticket_service.exception.reservation;

import dev.bum.common.error.ErrorCode;

public class ReservationCancellationInProgressException extends RuntimeException {
    public ReservationCancellationInProgressException(Throwable cause) {
        super(ErrorCode.RESERVATION_CANCELLATION_IN_PROGRESS.getMessage(), cause);
    }
}
