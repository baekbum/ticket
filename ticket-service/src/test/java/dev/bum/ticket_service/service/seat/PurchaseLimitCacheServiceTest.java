package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PurchaseLimitCacheServiceTest {
    @Mock private TicketJpaRepository ticketRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    private PurchaseLimitCacheService service;
    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(TicketStatus.PENDING_PAYMENT, TicketStatus.PAID);

    @BeforeEach
    void setUp() {
        service = new PurchaseLimitCacheService(ticketRepository, redis);
        given(redis.opsForValue()).willReturn(values);
    }

    @Test
    void refresh_uses_current_database_count_without_incrementing() {
        Event event = Event.builder().eventId(1L).ticketLimitScope(TicketLimitScope.PER_EVENT).build();
        given(ticketRepository.countByUserIdAndEventAndStatusIn("user01", event, ACTIVE_STATUSES)).willReturn(3L);

        service.refresh(event, "user01");
        service.refresh(event, "user01");

        then(values).should(times(2)).set("user:purchase:limit:event:1:user01", "3", Duration.ofDays(30));
        then(values).shouldHaveNoMoreInteractions();
    }

    @Test
    void refresh_uses_group_count_and_key() {
        Event event = Event.builder().eventId(1L).ticketLimitScope(TicketLimitScope.PER_GROUP)
                .eventGroupCode("CONCERT").build();
        given(ticketRepository.countByUserIdAndEvent_EventGroupCodeAndStatusIn("user01", "CONCERT", ACTIVE_STATUSES))
                .willReturn(2L);

        service.refresh(event, "user01");

        then(values).should().set("user:purchase:limit:group:CONCERT:user01", "2", Duration.ofDays(30));
        then(ticketRepository).should().countByUserIdAndEvent_EventGroupCodeAndStatusIn("user01", "CONCERT", ACTIVE_STATUSES);
        then(ticketRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void refresh_restores_zero_after_all_tickets_are_cancelled() {
        Event event = Event.builder().eventId(1L).ticketLimitScope(TicketLimitScope.PER_EVENT).build();
        given(ticketRepository.countByUserIdAndEventAndStatusIn("user01", event, ACTIVE_STATUSES)).willReturn(0L);

        service.refresh(event, "user01");

        then(values).should().set("user:purchase:limit:event:1:user01", "0", Duration.ofDays(30));
    }
}
