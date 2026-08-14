package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private QueueAccessService queueAccessService;

    @InjectMocks
    private CheckoutService checkoutService;

    @Test
    @DisplayName("checkout 준비 요청은 active token과 좌석 선점 상태를 검증한다")
    void prepare_validates_active_token_and_occupied_seats() {
        CheckoutPrepareRequest request = checkoutRequest("  idem-1  ");

        CheckoutPrepareResponse response = checkoutService.prepare("user01", "queue-token", request);

        assertThat(response.isPrepared()).isTrue();
        assertThat(response.getEventId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getSeats()).hasSize(1);
        assertThat(response.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(response.getPreparedAt()).isNotNull();

        then(queueAccessService).should().validate(1L, "user01", "queue-token");
        then(seatCacheService).should().validateOccupiedSeat(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("user01"),
                org.mockito.ArgumentMatchers.eq("order-1"),
                org.mockito.ArgumentMatchers.argThat(seats -> seats.size() == 1)
        );
        then(queueAccessService).should().complete(1L, "user01", "queue-token");
    }

    @Test
    @DisplayName("checkout 준비 요청에 멱등 키가 없으면 거부한다")
    void prepare_rejects_missing_idempotency_key() {
        CheckoutPrepareRequest request = checkoutRequest(" ");

        assertThatThrownBy(() -> checkoutService.prepare("user01", "queue-token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 멱등 키가 필요합니다.");

        then(queueAccessService).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("checkout 준비 성공 후 active token 회수는 트랜잭션 커밋 이후 실행된다")
    void prepare_releases_active_token_after_commit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            CheckoutPrepareRequest request = checkoutRequest("idem-1");

            checkoutService.prepare("user01", "queue-token", request);

            then(queueAccessService).should().validate(1L, "user01", "queue-token");
            then(queueAccessService).should(never()).complete(1L, "user01", "queue-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            then(queueAccessService).should().complete(1L, "user01", "queue-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CheckoutPrepareRequest checkoutRequest(String idempotencyKey) {
        return CheckoutPrepareRequest.builder()
                .orderId("order-1")
                .eventId(1L)
                .seats(List.of(SeatInfo.builder()
                        .id(1L)
                        .zone("VIP")
                        .row(1)
                        .col(1)
                        .build()))
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
