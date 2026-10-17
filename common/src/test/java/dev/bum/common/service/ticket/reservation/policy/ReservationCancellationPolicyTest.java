package dev.bum.common.service.ticket.reservation.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationCancellationPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-15T23:49:59", "2026-10-15T23:50:00", "2026-10-16T00:00:00"})
    void allowsRequestsThroughCutoffAndReopensAtMidnight(String time) {
        assertThatCode(() -> ReservationCancellationPolicy.validateNewRequest(LocalDateTime.parse(time), null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-15T23:50:00.000000001", "2026-10-15T23:59:59"})
    void rejectsRequestsAfterDailyCutoff(String time) {
        assertThatThrownBy(() -> ReservationCancellationPolicy.validateNewRequest(LocalDateTime.parse(time), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampsFinalDeadlineWithoutExtendingAnEarlierDeadline() {
        assertThat(ReservationCancellationPolicy.effectiveDeadline(LocalDateTime.parse("2026-10-15T23:59:59")))
                .isEqualTo(LocalDateTime.parse("2026-10-15T23:50:00"));
        assertThat(ReservationCancellationPolicy.effectiveDeadline(LocalDateTime.parse("2026-10-15T17:00:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-15T17:00:00"));
        assertThatThrownBy(() -> ReservationCancellationPolicy.validateNewRequest(
                LocalDateTime.parse("2026-10-16T00:00:00"), LocalDateTime.parse("2026-10-15T23:59:59")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
