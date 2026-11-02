package dev.bum.common;

import dev.bum.common.service.ticket.event.event.policy.EventBookingPolicy;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class EventBookingPolicyTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 29, 10, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 10, 30, 23, 59, 59);
    private final LocalDateTime performance = LocalDateTime.of(2026, 10, 31, 18, 0);

    @Test void start_and_end_are_inclusive_but_holds_cannot_extend_sales() {
        EventBookingPolicy.validate(EventStatus.ON_SALE, start, end, performance, start, false);
        EventBookingPolicy.validate(EventStatus.ON_SALE, start, end, performance, end, true);
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.ON_SALE,
                start, end, performance, end.plusNanos(1), true)).hasMessage("판매가 종료된 공연입니다.");
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.ON_SALE,
                start, end, performance, start.minusNanos(1), false)).hasMessageContaining("시작되지");
    }
    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "SALE_ENDED", "CLOSED"})
    void terminal_status_blocks_even_existing_holds(EventStatus status) {
        assertThatThrownBy(() -> EventBookingPolicy.validate(status, start, end, performance, start, true))
                .hasMessageContaining("공연 상태");
    }
    @Test void sold_out_allows_only_existing_holds_within_sale_period() {
        EventBookingPolicy.validate(EventStatus.SOLD_OUT, start, end, performance, start, true);
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.SOLD_OUT,
                start, end, performance, start, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.SOLD_OUT,
                start, end, performance, end.plusSeconds(1), true)).hasMessageContaining("종료");
    }
    @Test void incomplete_configuration_and_performance_start_block_booking() {
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.ON_SALE,
                null, end, performance, start, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventBookingPolicy.validate(EventStatus.ON_SALE,
                start, end.plusDays(2), performance, performance, true)).hasMessageContaining("종료");
    }
}
