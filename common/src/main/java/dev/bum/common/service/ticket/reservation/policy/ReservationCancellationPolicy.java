package dev.bum.common.service.ticket.reservation.policy;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class ReservationCancellationPolicy {
    public static final LocalTime DAILY_CUTOFF = LocalTime.of(23, 50);

    private ReservationCancellationPolicy() {
    }

    public static LocalDateTime effectiveDeadline(LocalDateTime configured) {
        if (configured == null) return null;
        LocalDateTime latest = configured.toLocalDate().atTime(DAILY_CUTOFF);
        return configured.isAfter(latest) ? latest : configured;
    }

    public static void validateNewRequest(LocalDateTime requestedAt, LocalDateTime deadline) {
        if (requestedAt.toLocalTime().isAfter(DAILY_CUTOFF)) {
            throw new IllegalArgumentException("신규 예매 취소는 매일 23시 50분 00초까지 가능합니다.");
        }

        LocalDateTime effective = effectiveDeadline(deadline);

        if (effective != null && requestedAt.isAfter(effective)) {
            throw new IllegalArgumentException("취소 가능 기한이 지났습니다.");
        }
    }
}
