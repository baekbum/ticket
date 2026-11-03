package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutbox;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatCacheSyncOutboxService {

    // 좌석 캐시 동기화 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final SeatCacheSyncOutboxJpaRepository repository;
    // 좌석 캐시 동기화 Outbox 이벤트 발행 대기 작업을 순회하고 실행하는 도구.
    private final SeatCacheSyncOutboxDispatcher dispatcher;

    /**
     * 좌석 캐시 동기화 Outbox 이벤트 처리에서 발행 대기 이벤트로 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(List<Seat> seats, String operation, String userId, String orderId) {

        if (seats.isEmpty()) return;

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("좌석 동기화를 위한 예매 소유 정보가 필요합니다.");
        }

        List<Long> outboxIds = new ArrayList<>();

        for (Seat seat : seats) {
            if (seat.getSeatId() == null) throw new IllegalArgumentException("좌석 정보가 올바르지 않습니다.");

            // Redis에 기록할 일을 좌석 변경과 같은 트랜잭션에 저장한다.
            SeatCacheSyncOutbox saved = repository.save(SeatCacheSyncOutbox.builder()
                    .seatId(seat.getSeatId())
                    .redisKey(SeatCacheSyncOutbox.keyOf(seat))
                    .lockValue("LOCKED:" + userId + ":" + orderId)
                    .operation(operation)
                    .build());
            outboxIds.add(saved.getOutboxId());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 트랜잭션 커밋이 완료된 뒤 등록한 후속 작업을 실행한다.
             */
            @Override
            public void afterCommit() {

                // 즉시 처리를 놓쳐도 커밋된 PENDING 작업은 스케줄러가 다시 처리한다.
                outboxIds.forEach(dispatcher::publishSafely);
            }
        });
    }
}
