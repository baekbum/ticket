package dev.bum.common.service.ticket.reservation.policy;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class ReservationCancellationPolicy {

    // 하루 중 신규 예매 취소를 허용하는 마감 시각.
    public static final LocalTime DAILY_CUTOFF = LocalTime.of(23, 50);

    /**
     * 외부에서 인스턴스를 생성하지 못하도록 제한한다.
     */
    private ReservationCancellationPolicy() {
    }

    /**
     * 설정한 취소 기한과 당일 취소 마감 시각 중 더 이른 시각을 반환한다.
     */
    public static LocalDateTime effectiveDeadline(LocalDateTime configured) {

        if (configured == null) return null;
        LocalDateTime latest = configured.toLocalDate().atTime(DAILY_CUTOFF);
        return configured.isAfter(latest) ? latest : configured;
    }

    /**
     * 신규 취소 요청이 당일 마감 시각과 예매 취소 기한을 지키는지 검증한다.
     */
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
